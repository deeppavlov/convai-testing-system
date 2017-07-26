#!/usr/bin/env python3

import sys
import json
import random
lines = sys.stdin.readlines()
for line in lines:
    d = json.loads(line)
    a_index = random.randint(0, 1)
    b_index = 1 - a_index

    for t in d['thread']:
        t['userId'] = "Alice" if t['userId'] == d['users'][a_index]['id'] else "Bob"

    """
    В базе для Alice (Bob) хранятся оценки которые она ПОСТАВИЛА
    В выгрузке для Alice (Bob) будут выводиться оценки которые она ПОЛУЧИЛА
    Для этого меняем местами оценки Alice и Bob соотвествено 
    """
    for e in d['evaluation']:
        e['userId'] = "Bob" if e['userId'] == d['users'][a_index]['id'] else "Alice"

    d['users'][a_index]['id'] = "Alice"
    d['users'][b_index]['id'] = "Bob"
    print(json.dumps(d))
