#!/usr/bin/env python3

import sys
import json

from .common import team_users_lower, dialog_min_len


def calc_score(q):
    if len(q) > 0:
        return sum(q) / float(len(q))
    else:
        return 0


user_evaluations = dict()
user_names = dict()
user_bots = dict()

lines = sys.stdin.readlines()
for line in lines:
    d = json.loads(line)
    if d['users'][0]['userType'] == 'org.pavlovai.communication.Bot':
        bot_id = d['users'][0]['id']
        user_id = d['users'][1]['id']
        username = d['users'][1]['username']
        user_names[bot_id] = bot_id
        user_names[user_id] = username
        if username not in user_bots:
            user_bots[username] = 0
        if dialog_min_len(d['thread']) > 2:
            user_bots[username] += 1

    elif d['users'][1]['userType'] == 'org.pavlovai.communication.Bot':
        bot_id = d['users'][1]['id']
        user_id = d['users'][0]['id']
        username = d['users'][0]['username']
        user_names[bot_id] = bot_id
        user_names[user_id] = username
        if username not in user_bots:
            user_bots[username] = 0
        if dialog_min_len(d['thread']) > 2:
            user_bots[username] += 1
    else:
        bot_id = None
        user_names[d['users'][0]['id']] = d['users'][0]['username']
        user_names[d['users'][1]['id']] = d['users'][1]['username']

    user0 = d['users'][0]['id']
    user1 = d['users'][1]['id']

    if user0 not in user_evaluations:
        user_evaluations[user0] = []

    if user1 not in user_evaluations:
        user_evaluations[user1] = []

    for e in d['evaluation']:
        if e['userId'] != bot_id:
            if e['userId'] == user0:
                user_evaluations[user1].append(e['quality'])
            elif e['userId'] == user1:
                user_evaluations[user0].append(e['quality'])
        else:
            continue

max_user_score = 0
for u_id in user_evaluations:
    if user_names[u_id].lower() in team_users_lower:
        max_user_score = max(max_user_score, calc_score(user_evaluations[u_id]))

max_user_bots = 0
for u_name in user_bots:
    if u_name.lower() in team_users_lower:
        max_user_bots = max(user_bots[u_name], max_user_bots)

leaderboard = []
for u_id in user_evaluations:
    user_bot = 0 if user_names[u_id] not in user_bots else user_bots[user_names[u_id]]
    user_score = calc_score(user_evaluations[u_id])
    if user_names[u_id].lower() in team_users_lower:
        score = 0.5 * (user_bot / max_user_bots + user_score/max_user_score)
        leaderboard.append((user_names[u_id], score))

leaderboard.sort(key=lambda tup: tup[1], reverse=True)
for item in leaderboard:
    print("%s,%s" % item)
