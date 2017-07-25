#!/usr/bin/env python3

import sys
import json

lines = sys.stdin.readlines()
users = dict()
usernames = dict()
for line in lines:
    d = json.loads(line)
    if d['users'][0]['userType'] == 'org.pavlovai.communication.Bot':
        bot_id = d['users'][0]['id']
        usernames[bot_id] = bot_id
        usernames[d['users'][1]['id']] = d['users'][1]['username']
    elif d['users'][1]['userType'] == 'org.pavlovai.communication.Bot':
        bot_id = d['users'][1]['id']
        usernames[bot_id] = bot_id
        usernames[d['users'][0]['id']] = d['users'][0]['username']
    else:
        bot_id = None
        usernames[d['users'][0]['id']] = d['users'][0]['username']
        usernames[d['users'][1]['id']] = d['users'][1]['username']

    user0 = d['users'][0]['id']
    user1 = d['users'][1]['id']

    if user0 not in users:
        users[user0] = []

    if user1 not in users:
        users[user1] = []

    for e in d['evaluation']:
        if e['userId'] != bot_id:
            if e['userId'] == user0:
                users[user1].append(e['quality'])
            elif e['userId'] == user1:
                users[user0].append(e['quality'])
        else:
            continue

for u in users:
    q = users[u]
    if len(q) > 0:
        score = sum(q) / float(len(q))
    else:
        score = 0
    print("%s,%s,%s" % (usernames[u], len(q), score))

