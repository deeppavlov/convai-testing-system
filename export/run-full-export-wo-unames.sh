cd ./nips_router_bot/utils
echo "Export dataset without usernames"
./export_ds.bash convai-bot $1 | ./alice_bob.py | ./username_clean.py | ./combine.py > ../../day$1/no_uname_ds.json





