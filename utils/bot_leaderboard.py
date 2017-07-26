import sys
import json


def dialog_min_len(thread):
    dialog = dict()
    for t in thread:
        if t['userId'] not in dialog:
            dialog[t['userId']] = 0
        dialog[t['userId']] += 1
    return 0 if len(dialog.values()) == 0 else min(dialog.values())


def calc_score(q):
    if len(q) > 0:
        return sum(q) / float(len(q))
    else:
        return 0

bot_names = {
    "4DEC9F55-3475-4AAC-B990-37A7D9955792": "DeepTalkHawk",
    "5319E57A-F165-4BEC-94E6-413C38B4ACF9": "RLLChatBot",
    "F0690A4D-B999-46F0-AD14-C65C13F09C40": "bot#1337",
    "CCDC13A5-4CD0-457F-B7A3-220C2CC0F478": "Q&A",
    "DA008C35-73CD-4A64-8D67-5C922808D6B4": "kAIb",
    "378EFFA0-5A04-42E4-A5E8-C4D80B84FE7F": "Chatme",
    "0A36119D-E6C0-4022-962F-5B5BDF21FD97": "PolyU",
    "BF2D7373-30B9-4DD3-AD90-A6E452D7F7BB": "ABR-KNU",
    "28F55050-5033-47F1-BA54-F764B24D0B7B": "poetwannabe",
    "A28986EA-AA0A-4F9F-BD23-BF3B9EE5BE9C": "Noname",
    "1d4360a4-714f-11e7-bc4c-df9c5b96bd08": "DATA Siegt",
    "1686e6ee-71e2-11e7-bdb8-5f3547bb4fc8": "Plastic World"
}

bot_evaluations = dict()

lines = sys.stdin.readlines()
for line in lines:
    d = json.loads(line)
    if d['users'][0]['userType'] == 'org.pavlovai.communication.Bot':
        bot_id = d['users'][0]['id']
    elif d['users'][1]['userType'] == 'org.pavlovai.communication.Bot':
        bot_id = d['users'][1]['id']
    else:
        bot_id = None

    if bot_id is not None and dialog_min_len(d['thread']) > 2:
        bot_name = bot_names[bot_id]
        if bot_name not in bot_evaluations:
            bot_evaluations[bot_name] = []

        for e in d['evaluation']:
            if e['userId'] != bot_id:
                bot_evaluations[bot_name].append(e['quality'])

leaderboard = []
for bot in bot_evaluations:
    leaderboard.append((bot, calc_score(bot_evaluations[bot])))

leaderboard.sort(key=lambda tup: tup[1], reverse=True)
for item in leaderboard:
    print("%s,%s" % item)
