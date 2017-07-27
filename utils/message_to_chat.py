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
    bot.send_message(chat_id, "`(system msg):` What is your English language proficiency?", reply_markup=markup, parse_mode='MARKDOWN')
