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

team_users = ['Ignecadus', 'locky_kid', 'IFLED', 'justgecko', 'AlexFridman', 'necnec', 'YallenGusev', 'fartuk1',
              'mryab', 'akiiino', 'vostrjakov', 'chernovsergey', 'latentbot', 'SkifMax', 'VictorPo', 'zhukov94',
              'Username11235', 'IlyaValyaev', 'lextal', 'MacJIeHok', 'olgalind', 'roosh_roosh', 'davkhech',
              'mambreyan', 'ashmat98', 'ffuuugor', 'artyomka', 'p_gladkov', 'not_there', 'ad3002', 'gtamazian',
              'artkorenev', 'sudakovoleg', 'sin_mike', 'ilya_shenbin', 'Vladislavprh', 'AntonAlexeyev',
              'bekerov', 'EvKosheleva', 'sw1sh', 'SDrapak', 'izmailov', 'dlunin', 'Xsardas', 'sparik'
              ]
team_users_lower = [t.lower() for t in team_users]


def dialog_min_len(thread):
    dialog = dict()
    for t in thread:
        if t['userId'] not in dialog:
            dialog[t['userId']] = 0
        dialog[t['userId']] += 1
    return 0 if len(dialog.values()) == 0 else min(dialog.values())