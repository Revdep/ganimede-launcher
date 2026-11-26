#!/usr/bin/env bash
# Usage: check-versioncode.sh — fails unless versionCode in build.gradle is greater than at the previous v* tag.
# Needs the tags fetched (checkout fetch-depth: 0). No previous tag: passes.
set -euo pipefail
code() { sed -n 's/^ *versionCode \([0-9]*\)$/\1/p'; }
now=$(code < build.gradle)
[ -n "$now" ] || { echo "versionCode not found in build.gradle"; exit 1; }
prev=$(git tag --list 'v*' --sort=-v:refname --merged HEAD | grep -vxF "${GITHUB_REF_NAME:-}" | head -1 || true)
[ -n "$prev" ] || { echo "no previous tag: versionCode $now accepted"; exit 0; }
old=$(git show "$prev:build.gradle" | code)
[ "$now" -gt "$old" ] || { echo "versionCode $now is not greater than $old at $prev"; exit 1; }
