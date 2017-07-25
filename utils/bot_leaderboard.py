#!/usr/bin/env python3

import sys
import json

lines = sys.stdin.readlines()
bots = dict()
for line in lines:
    d = json.loads(line)
    if d['users'][0]['userType'] == 'org.pavlovai.communication.Bot':
        bot_id = d['users'][0]['id']
        user_id = d['users'][1]['id']
    elif d['users'][1]['userType'] == 'org.pavlovai.communication.Bot':
        bot_id = d['users'][1]['id']
        user_id = d['users'][0]['id']
    else:
        bot_id = None
        user_id = None
        continue

    if bot_id not in bots:
        bots[bot_id] = []

    for e in d['evaluation']:
        if e['userId'] == user_id:
            bots[bot_id].append(e['quality'])

for bot in bots:
    q = bots[bot]
    print("Bot %s score: %s" % (bot, sum(q) / float(len(q))))

