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

mongoexport --host ${host} --db ${db} --collection dialogs --fields 'dialogId,users,context,evaluation,thread' --query "$(./oid.py ${days})" -u ${user} -p ${password}