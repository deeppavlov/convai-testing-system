cd ./nips_router_bot/utils
echo "Export raw dataset"
./export_ds.bash convai-bot $1 > ../../day$1/raw_fds.json
echo "Create alice-bot dataset"
cat ../../day$1/raw_fds.json | ./alice_bob.py > ../../day$1/alice_bob_fds.json
echo "Create train dataset"
cat ../../day$1/alice_bob_fds.json | ./train_clean.py | ./combine.py > ../../day$1/train_fds.json
echo "Create test dataset"
cat ../../day$1/alice_bob_fds.json | ./test_clean.py  | ./combine.py > ../../day$1/test_fds.json




