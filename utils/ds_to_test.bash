#!/usr/bin/env bash

host=${1}
shift
db=${1}
shift
user=${1}
shift
password=${1}
shift
days=${1}
shift

./export_ds.bash ${host} ${db} ${user} ${password} ${days} | jq '{users: .users[] | {id: .id}, context, dialogId, thread: .thread[] | {userId: .userId, text: .text}}'