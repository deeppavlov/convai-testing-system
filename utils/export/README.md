## Notice on paths
The export scripts is supposed to be placed three levels of hierarchy higher than ```./export``` folder. 

So we propose to use command like this: ```for F in *.sh; do ln -s $F ../../../; done``` 

Internally the path for the project still set to ```nips_router_bot```, so you need to name the formed with cloned repo accordingly. 

## Scripts
* [calc_stat.sh](calc_stat.sh) - calculates statistics for teams for specified day: ```./calc_stat.sh [YYYYMMDD]```
* [calc_daily_leaderboard.sh](calc_daily_leaderboard.sh) - calculates leader board on whole dataset: ```./calc_daily_leaderboard.sh```
* [calc_team_leaderboard.sh](calc_team_leaderboard.sh) - calculates leaderboard for each exported team: ```calc_team_leaderboard.sh```

</br>

* [run-export.sh](run-export.sh) - generates dataset for specified day (holdout set excluded): ```./run-export.sh [YYYYMMDD]```
* [run-full-export.sh](run-full-export.sh) - generates dataset for specified day (holdout set _included_): ```./run-full-export.sh [YYYYMMDD]```
* [run-full-export-wo-usernames.sh](./run-full-export-wo-usernames.sh) - same as above, usernames are hidden: ```./run-full-export-wo-usernames.sh [YYYYMMDD]```
* [run-holdout-export.sh](run-holdout-export.sh) - generates holdout set for specified day: ```./run-holdout-export.sh [YYYYMMDD]```
