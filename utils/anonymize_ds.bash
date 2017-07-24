#!/usr/bin/env bash

jq '{users: .users[] | {id: .id}, context, dialogId, thread: .thread[] | {userId: .userId, text: .text}}'