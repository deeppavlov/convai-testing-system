#!/usr/bin/env python3

import sys
import json

from common import bot_names, dialog_min_len

lines = sys.stdin.readlines()

dialogs = dict()

for line in lines:
    d = json.loads(line)
    if dialog_min_len(d['thread']) > 2:
        if d['users'][0]['userType'] == 'org.pavlovai.communication.Bot':
            bot_id = d['users'][0]['id']
        elif d['users'][1]['userType'] == 'org.pavlovai.communication.Bot':
            bot_id = d['users'][1]['id']
        else:
            bot_id = "Human"

        if bot_id not in dialogs:
            dialogs[bot_id] = 0

        dialogs[bot_id] += 1

print("Total dialogs: %s" % sum(dialogs.values()))
for d in dialogs:
    name = d if d not in bot_names else "Bot %s" % bot_names[d]
    print("%s : %s" % (name, dialogs[d]))


