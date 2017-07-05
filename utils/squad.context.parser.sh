#!/usr/bin/env bash

curl -s https://rajpurkar.github.io/SQuAD-explorer/dataset/train-v1.1.json | jq --raw-output '.data[].paragraphs[].context'