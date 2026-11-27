#!/usr/bin/env bash
# Runs e2e/run.sh on the first free test emulator, waiting while all are busy (e2e/shard.sh with one shard).
# Usage: [ONLY=…] e2e/any.sh
SHARDS=1 exec "$(dirname "$0")/shard.sh"
