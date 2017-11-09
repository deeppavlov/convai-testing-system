cd ./nips_router_bot/utils
echo "Export raw dataset"
./export_ds.bash convai-bot $1 > ../../day$1/raw_ds.json
echo "Create alice-bot dataset"
cat ../../day$1/raw_ds.json | ./alice_bob.py > ../../day$1/alice_bob_ds.json
echo "Create train dataset"
cat ../../day$1/alice_bob_ds.json | ./train_clean.py | awk 'NR % 10 !=3 && NR % 10 != 5' | ./combine.py > ../../day$1/train_ds.json
echo "Create test dataset"
cat ../../day$1/alice_bob_ds.json | ./test_clean.py  | awk 'NR % 10 !=3 && NR % 10 != 5' | ./combine.py > ../../day$1/test_ds.json




