#!/usr/bin/env bash
# Usage: check-tag.sh vX.Y.Z — fails unless the tag matches versionName in build.gradle.
set -euo pipefail
name=$(sed -n "s/^ *versionName '\(.*\)'$/\1/p" build.gradle)
[ -n "$name" ] || { echo "versionName not found in build.gradle"; exit 1; }
[ "$1" = "v$name" ] || { echo "tag $1 differs from versionName $name"; exit 1; }
