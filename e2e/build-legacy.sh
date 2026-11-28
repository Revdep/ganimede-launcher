#!/usr/bin/env bash
# Builds the APK users already have, main's (the merge-base of HEAD with origin/main, or the commit given), signed with the
# same debug key, into e2e/legacy/ganimede-main.apk. 20-appicon's upgrade step installs it, then this build over it.
# The CI build job runs it beside the other APKs; locally run it before the suite (it rebuilds only when main moved: the
# commit of the APK is written beside it, e2e/legacy/ganimede-main.commit, and 20-appicon refuses an APK of another commit).
# Usage: e2e/build-legacy.sh [commit]   (a commit of this repository, whose history it needs: CI checks out with fetch-depth 0)
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
COMMIT="${1:-$(git -C "$HERE/.." merge-base origin/main HEAD)}"
COMMIT="$(git -C "$HERE/.." rev-parse "$COMMIT")"
[ "$(cat "$HERE/legacy/ganimede-main.commit" 2>/dev/null)" = "$COMMIT" ] && [ -f "$HERE/legacy/ganimede-main.apk" ] \
  && { echo "e2e/legacy/ganimede-main.apk: already main at ${COMMIT:0:7}"; exit 0; }
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir "$TMP/src"
git -C "$HERE/.." archive "$COMMIT" | tar -x -C "$TMP/src"
# git archive leaves out the ignored local.properties (the SDK path); gradle runs in the shared queue (CLAUDE.md rule 17)
# (a worktree has none of its own: the main checkout's)
for lp in "$HERE/../local.properties" "$(git -C "$HERE/.." rev-parse --git-common-dir)/../local.properties"; do
  [ -f "$lp" ] && { cp "$lp" "$TMP/src/"; break; }
done
[ -f "$TMP/src/local.properties" ] || [ -n "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}" ] || echo "build-legacy: no local.properties and no ANDROID_HOME: gradle will not find the SDK" >&2
(cd "$TMP/src" && flock /tmp/ganimede-gradle.lock ./gradlew -q assembleDebug >&2)
mkdir -p "$HERE/legacy"
cp "$TMP/src/Ganimede-Launcher-debug.apk" "$HERE/legacy/ganimede-main.apk"
echo "$COMMIT" > "$HERE/legacy/ganimede-main.commit"
echo "e2e/legacy/ganimede-main.apk: main at ${COMMIT:0:7}"
