#!/usr/bin/env bash
# Builds the APK users already have, main's (the merge-base of HEAD with origin/main, or the commit given), signed with the
# same debug key, into e2e/legacy/ganimede-main.apk. 20-appicon's upgrade step installs it, then this build over it.
# The CI build job runs it beside the other APKs; locally run it once before the suite.
# Usage: e2e/build-legacy.sh [commit]   (a commit of this repository, whose history it needs: CI checks out with fetch-depth 0)
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
COMMIT="${1:-$(git -C "$HERE/.." merge-base origin/main HEAD)}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir "$TMP/src"
git -C "$HERE/.." archive "$COMMIT" | tar -x -C "$TMP/src"
(cd "$TMP/src" && ./gradlew -q assembleDebug >&2)
mkdir -p "$HERE/legacy"
cp "$TMP/src/Ganimede-Launcher-debug.apk" "$HERE/legacy/ganimede-main.apk"
echo "e2e/legacy/ganimede-main.apk: main at $(git -C "$HERE/.." rev-parse --short "$COMMIT")"
