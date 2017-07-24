#!/usr/bin/env python3

import roc_auc_score from sklearn.metrics
import numpy as np
import pandas as pd
import sys
import json

if len(sys.argv) != 3:
    print("example: leaderboard.py <dataset.json> <labels.csv>", file=sys.stderr)
    sys.exit(1)

dataset_file = sys.argv[1]
labels_file = sys.argv[2]

df = pd.read_csv(labels_file, header = 0)
df[[0]]