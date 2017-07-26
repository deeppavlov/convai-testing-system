#!/usr/bin/env python3

import sys
import json

from .common import bot_names, dialog_min_len


def calc_score(q):
    if len(q) > 0:
        return sum(q) / float(len(q))
    else:
        return 0

bot_evaluations = dict()

lines = sys.stdin.readlines()
for line in lines:
    d = json.loads(line)
    if d['users'][0]['userType'] == 'org.pavlovai.communication.Bot':
        bot_id = d['users'][0]['id']
    elif d['users'][1]['userType'] == 'org.pavlovai.communication.Bot':
        bot_id = d['users'][1]['id']
    else:
        bot_id = None

    if bot_id is not None and dialog_min_len(d['thread']) > 2:
        bot_name = bot_names[bot_id]
        if bot_name not in bot_evaluations:
            bot_evaluations[bot_name] = []

        for e in d['evaluation']:
            if e['userId'] != bot_id:
                bot_evaluations[bot_name].append(e['quality'])

leaderboard = []
for bot in bot_evaluations:
    leaderboard.append((bot, calc_score(bot_evaluations[bot])))

leaderboard.sort(key=lambda tup: tup[1], reverse=True)
for item in leaderboard:
    print("%s,%s" % item)
