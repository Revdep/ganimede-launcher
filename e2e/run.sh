#!/usr/bin/env bash
# End-to-end usability test on an emulator, one file per section in sections/NN-name.sh.
# Usage: [ONLY="drawer|list"] e2e/run.sh [serial]   (default emulator-5554). ONLY: regex on the section names.
# Several emulators at once: e2e/shard.sh (PLAN below splits the sections).
# Exit: 0 all passed, 1 a check failed (or a section is broken, ran no check or crashed the app), 2 the setup refused.
# Screenshots go to e2e/out/<serial>/. A section that needs a configured app calls `seed <mode>`; only `wizard` installs fresh.
set -u
SERIAL="${1:-emulator-5554}"
HERE="$(cd "$(dirname "$0")" && pwd)"
APK="$HERE/../Ganimede-Launcher-debug.apk"
bash "$HERE/guard.sh" || exit 2 # helpers that exist against a race are not bypassed (see guard.sh)
picked=()
for f in "$HERE"/sections/[0-9]*.sh; do
  name=$(basename "$f" .sh); name=${name#*-}
  [ -z "${ONLY:-}" ] || echo "$name" | grep -Eq "$ONLY" && picked+=("$f")
done
[ ${#picked[@]} -gt 0 ] || { echo "no section matches ONLY=${ONLY:-}" >&2; exit 2; }
TIMES="$HOME/.cache/ganimede/e2e-times" # "name seconds" after every section run; the last line of a name counts
if [ -n "${PLAN:-}" ]; then # PLAN=n: print n ONLY regexes splitting the picked sections by time (longest first, onto the lightest), run nothing
  # PLAN=auto: n = ceil(total seconds / PLAN_TARGET), 1..18 (PLAN_TARGET default 300), so a longer suite adds shards instead of minutes
  for f in "${picked[@]}"; do name=$(basename "$f" .sh); echo "${name#*-}"; done \
    | awk -v f="$TIMES" 'BEGIN { while ((getline l < f) > 0) { split(l, a, " "); t[a[1]] = a[2] } } { print ($1 in t ? t[$1] : 120), $1 }' \
    | sort -nr | awk -v k="$PLAN" -v target="${PLAN_TARGET:-300}" '{ w[NR] = $1; n[NR] = $2; sum += $1 }
      END { if (k == "auto") { k = int((sum + target - 1) / target); if (k > 18) k = 18; if (k < 1) k = 1 }
        for (r = 1; r <= NR; r++) { i = 0; for (j = 1; j < k; j++) if (load[j] < load[i]) i = j; load[i] += w[r]; s[i] = s[i] (s[i] == "" ? "" : "|") n[r] }
        for (j = 0; j < k; j++) if (s[j] != "") print "^(" s[j] ")$" }'
  exit 0
fi
. "$HERE/lib.sh"

# a stale APK is a green run on the old build
if [ -n "$(find "$HERE/../src/main" "$HERE/../build.gradle" -newer "$APK" -type f -print -quit)" ]; then echo "$APK is older than the sources: ./gradlew assembleDebug first." >&2; exit 2; fi
case "${picked[*]}" in *-wizard.sh*) ;; *) A install -r "$APK" >/dev/null || { echo "install failed" >&2; exit 1; };; esac

for f in "${picked[@]}"; do
  CUR=$(basename "$f" .sh); t=$SECONDS; before=$((PASS + FAIL + SKIPS))
  if bash -n "$f"; then . "$f"; else ko "$CUR: syntax error"; fi
  step_time
  [ $((PASS + FAIL + SKIPS)) -gt $before ] || ko "$CUR: ran no check (nor a skip)"
  system_check # before the crash check: a restart's DeadSystemException lines are no crash of Ganimede's (system_check clears them)
  if A logcat -d -b crash | grep -q "org.ganimede.launcher"; then ko "$CUR: the app crashed"; A logcat -d -b crash | grep -A5 "org.ganimede" | head -20; A logcat -b crash -c; fi
  echo "  [$CUR: $((SECONDS - t)) s]"; mkdir -p "${TIMES%/*}"
  # shards share the file: append and cut under one lock (a lock on the file itself would die with the mv); the last 200 runs of sections are plenty
  ( flock 8; echo "${CUR#*-} $((SECONDS - t))" >> "$TIMES"; tail -n 200 "$TIMES" > "$TIMES.$$.tmp" && mv "$TIMES.$$.tmp" "$TIMES" ) 8>"$TIMES.lock"; CUR=
done

echo; echo "Result: $PASS passed, $FAIL failed. Screenshots in $OUT"
[ $FAIL -eq 0 ]
