#!/usr/bin/env python3

import sys
import json
lines = sys.stdin.readlines()
for line in lines:
    d = json.loads(line)
    d.pop('_id')
    d.pop('evaluation')
    d['users'][0].pop('username')
    d['users'][1].pop('username')
    d['users'][0].pop('userType')
    d['users'][1].pop('userType')
    for t in d['thread']:
        t.pop('evaluation')
    print(json.dumps(d))
