#!/usr/bin/env bash
# End-to-end usability test on an emulator, one file per section in sections/NN-name.sh.
# Usage: [ONLY="drawer|list"] e2e/run.sh [serial]   (default emulator-5554). ONLY: regex on the section names.
# Exit: 0 all passed, 1 a check failed (or a section is broken, ran no check or crashed the app), 2 the setup refused.
# Screenshots go to e2e/out/. A section that needs a configured app calls `seed <mode>`; only `wizard` installs fresh.
set -u
SERIAL="${1:-emulator-5554}"
HERE="$(cd "$(dirname "$0")" && pwd)"
APK="$HERE/../Ganimede-Launcher-debug.apk"
. "$HERE/lib.sh"

picked=()
for f in "$HERE"/sections/[0-9]*.sh; do
  name=$(basename "$f" .sh); name=${name#*-}
  [ -z "${ONLY:-}" ] || echo "$name" | grep -Eq "$ONLY" && picked+=("$f")
done
[ ${#picked[@]} -gt 0 ] || { echo "no section matches ONLY=${ONLY:-}" >&2; exit 2; }
# a stale APK is a green run on the old build
if [ -n "$(find "$HERE/../src/main" "$HERE/../build.gradle" -newer "$APK" -type f -print -quit)" ]; then echo "$APK is older than the sources: ./gradlew assembleDebug first." >&2; exit 2; fi
case "${picked[*]}" in *-wizard.sh*) ;; *) A install -r "$APK" >/dev/null || { echo "install failed" >&2; exit 1; };; esac

for f in "${picked[@]}"; do
  CUR=$(basename "$f" .sh); t=$SECONDS; before=$((PASS + FAIL + SKIPS))
  if bash -n "$f"; then . "$f"; else ko "$CUR: syntax error"; fi
  [ $((PASS + FAIL + SKIPS)) -gt $before ] || ko "$CUR: ran no check (nor a skip)"
  if A logcat -d -b crash | grep -q "org.ganimede.launcher"; then ko "$CUR: the app crashed"; A logcat -d -b crash | grep -A5 "org.ganimede" | head -20; A logcat -b crash -c; fi
  echo "  [$CUR: $((SECONDS - t)) s]"; CUR=
done

echo; echo "Result: $PASS passed, $FAIL failed. Screenshots in $OUT"
[ $FAIL -eq 0 ]
