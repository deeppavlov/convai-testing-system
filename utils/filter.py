#!/usr/bin/env python3

import sys
import json
import random

if len(sys.argv) != 2:
    print("Example: filter_by_len.py <min number of replies>", file=sys.stderr)
    sys.exit(1)

lines = sys.stdin.readlines()
threshold = int(sys.argv[1])
rejected = 0
for line in lines:
    d = json.loads(line)
    user0_cnt = 0
    user1_cnt = 0
    for t in d['thread']:
        if t['userId'] == d['users'][0]['id']:
            user0_cnt += 1
        elif t['userId'] == d['users'][1]['id']:
            user1_cnt += 1
        else:
            print(json.dumps(d))
            raise Exception("Unknown user!")
    if user0_cnt > threshold and user1_cnt > threshold:
        print(json.dumps(d))
    else:
        rejected += 1
print("Rejected %s dialogs" % rejected, file=sys.stderr)
