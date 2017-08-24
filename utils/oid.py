#!/usr/bin/env python3
 
# using pymongo-2.2
from bson.objectid import ObjectId
import datetime
import sys
from dateutil.tz import tzutc, tzlocal
 
deadlines = [
    (datetime.datetime(2017, 7, 24, 0, 0, 0, 0, tzlocal()), datetime.datetime(2020, 1, 1, 0, 0, 0, 0, tzlocal())),
    (datetime.datetime(2017, 7, 24, 0, 0, 0, 0, tzlocal()), datetime.datetime(2017, 7, 24, 22, 0, 0, 0, tzlocal())),
    (datetime.datetime(2017, 7, 24, 22, 0, 0, 0, tzlocal()), datetime.datetime(2017, 7, 25, 22, 0, 0, 0, tzlocal())),
    (datetime.datetime(2017, 7, 25, 22, 0, 0, 0, tzlocal()), datetime.datetime(2017, 7, 26, 22, 0, 0, 0, tzlocal())),
    (datetime.datetime(2017, 7, 26, 22, 0, 0, 0, tzlocal()), datetime.datetime(2017, 7, 27, 22, 0, 0, 0, tzlocal())),
    (datetime.datetime(2017, 7, 27, 22, 0, 0, 0, tzlocal()), datetime.datetime(2017, 7, 28, 22, 0, 0, 0, tzlocal())),
    (datetime.datetime(2017, 7, 28, 22, 0, 0, 0, tzlocal()), datetime.datetime(2017, 7, 29, 22, 0, 0, 0, tzlocal())),
    (datetime.datetime(2017, 7, 29, 22, 0, 0, 0, tzlocal()), datetime.datetime(2017, 7, 30, 22, 0, 0, 0, tzlocal())),
]

if len(sys.argv) != 2 or 0 > int(sys.argv[1]) >= len(deadlines):
    print("example: oid.py <competition day (1-7 or 0 for al days)>", file=sys.stderr)
    sys.exit(1)

day = int(sys.argv[1])

start_date = deadlines[day][0]
end_date = deadlines[day][1]

oid_start = ObjectId.from_datetime(start_date.astimezone(tzutc()))
oid_stop = ObjectId.from_datetime(end_date.astimezone(tzutc()))
 
print('{ "_id" : { "$gte" : { "$oid": "%s" }, "$lt" : { "$oid": "%s" } } }' % (str(oid_start), str(oid_stop)))
