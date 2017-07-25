#!/usr/bin/env python3
 
# using pymongo-2.2
from bson.objectid import ObjectId
import datetime
import sys
from dateutil.tz import tzutc, tzlocal
 
if len(sys.argv) != 2:
    print("example: oid.py <days>", file=sys.stderr)
    sys.exit(1)

now = datetime.datetime.now(tzlocal())
days = int(sys.argv[1])
yesterday = now - datetime.timedelta(days=days)
start_date = datetime.datetime(yesterday.year, yesterday.month, yesterday.day, 0, 0, 0, 0, tzlocal())
if days == 0:
    end_date = datetime.datetime(now.year, now.month, now.day, 23, 59, 59, 999, tzlocal())
else:
    end_date = datetime.datetime(now.year, now.month, now.day, 0, 0, 0, 0, tzlocal())
oid_start = ObjectId.from_datetime(start_date.astimezone(tzutc()))
oid_stop = ObjectId.from_datetime(end_date.astimezone(tzutc()))
 
print('{ "_id" : { "$gte" : { "$oid": "%s" }, "$lt" : { "$oid": "%s" } } }' % (str(oid_start), str(oid_stop)))
