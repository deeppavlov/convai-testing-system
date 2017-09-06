#!/usr/bin/env python3

import os
import sys
import telebot
from telebot import types


bot = telebot.TeleBot(os.environ['TOKEN'])

markup = types.ReplyKeyboardMarkup(row_width=1, one_time_keyboard=True)
itembtn1 = types.KeyboardButton('/Elementary')
itembtn2 = types.KeyboardButton('/Beginner')
itembtn3 = types.KeyboardButton('/Intermediate')
itembtn4 = types.KeyboardButton('/Fluent')
itembtn5 = types.KeyboardButton('/Native')
markup.row(itembtn1, itembtn2, itembtn3)
markup.row(itembtn4, itembtn5)

lines = sys.stdin.readlines()
for chat_id in lines:
    i = 0
    while True:
        try:
            print("processed: " + str(chat_id))
            bot.send_message(chat_id, "`(system msg):` What is your English language proficiency?", reply_markup=markup, parse_mode='MARKDOWN')
            break
        except Exception as e:
            print("error on message sending: " + str(e), file=sys.stderr)
            i = i + 1
            if i < 10:
                pass
            else:
                print("not processed user: " + str(chat_id), file=sys.stderr)
                break
