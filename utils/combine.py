#!/usr/bin/env python3

import sys
import json
lines = sys.stdin.readlines()
res = []
for line in lines:
    d = json.loads(line)
    res.append(d)
print("Combine %s objects" % len(res), file=sys.stderr)
print(json.dumps(res))
