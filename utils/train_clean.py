#!/usr/bin/env python3

import sys
import json
lines = sys.stdin.readlines()
for line in lines:
    d = json.loads(line)
    d.pop('_id')
    for e in d['evaluation']:
        e.pop('breadth')
        e.pop('engagement')
    for u in d['users']:
        u.pop('username')
        if u['userType'] == 'org.pavlovai.communication.Bot':
            u['userType'] = 'Bot'
        else:
            u['userType'] = 'Human'
    for t in d['thread']:
        t.pop('evaluation')
    print(json.dumps(d))
