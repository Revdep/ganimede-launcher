#!/usr/bin/env bash
# Builds an older APK that still offered the app icon chooser, signed with the same debug key, into
# e2e/legacy/ganimede-with-app-icon.apk. The e2e upgrade step needs it: the shell cannot enable a launcher alias itself.
# Usage: e2e/build-legacy.sh <repository> <commit>   (a git URL or path, and a commit of it that has the chooser)
set -euo pipefail
[ $# -eq 2 ] || { echo "usage: $0 <repository> <commit>" >&2; exit 2; }
REPO="$1"; COMMIT="$2"
HERE="$(cd "$(dirname "$0")" && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
git clone --quiet "$REPO" "$TMP/src" >&2
git -C "$TMP/src" checkout --quiet --detach "$COMMIT" >&2
(cd "$TMP/src" && ./gradlew --offline -q assembleDebug >&2)
mkdir -p "$HERE/legacy"
cp "$TMP/src/Ganimede-Launcher-debug.apk" "$HERE/legacy/ganimede-with-app-icon.apk"
