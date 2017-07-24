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
yesterday = now - datetime.timedelta(days=int(sys.argv[1]))
start_date = datetime.datetime(yesterday.year, yesterday.month, yesterday.day, 0, 0, 0, 0, tzlocal())
end_date = datetime.datetime(now.year, now.month, now.day, now.hour, now.minute, now.second, now.microsecond, tzlocal())
oid_start = ObjectId.from_datetime(start_date.astimezone(tzutc()))
oid_stop = ObjectId.from_datetime(end_date.astimezone(tzutc()))
 
print('{ "_id" : { "$gte" : { "$oid": "%s" }, "$lt" : { "$oid": "%s" } } }' % (str(oid_start), str(oid_stop)))
