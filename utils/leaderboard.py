#!/usr/bin/env python3

from sklearn.metrics import roc_auc_score
import sys
import json
import csv

if len(sys.argv) != 2:
    print("example: leaderboard.py <labels.csv>", file=sys.stderr)
    sys.exit(1)

lines = sys.stdin.readlines()

users_bot_flags = {}
for line in lines:
    d = json.loads(line)
    if d['users'][0]['id'] == 'Alice':
        users_bot_flags[d['dialogId']] = ( (int(d['users'][0]['userType'] == "org.pavlovai.communication.Bot"), int(d['users'][1]['userType'] == "org.pavlovai.communication.Bot")) )
    else:
        users_bot_flags[d['dialogId']] = ( (int(d['users'][1]['userType'] == "org.pavlovai.communication.Bot"), int(d['users'][0]['userType'] == "org.pavlovai.communication.Bot")) )

users_bot_predicted_probs = []
users_bot_fact_labaels = []
with open(sys.argv[1], 'r') as csvfile:
    spamreader = csv.reader(csvfile, delimiter=',', quotechar='|')
    for row in spamreader:
        dialog = int(row[0])
        if dialog in users_bot_flags:
            users_bot_fact_labaels.append(users_bot_flags[dialog][0])
            users_bot_predicted_probs.append(float(row[1]))

            users_bot_fact_labaels.append(users_bot_flags[dialog][1])
            users_bot_predicted_probs.append(float(row[2]))
        else:
            print("dialog " + dialog + " not in dataset", file=sys.stderr)

print(sys.argv[1], ": ", roc_auc_score(users_bot_fact_labaels, users_bot_predicted_probs))

