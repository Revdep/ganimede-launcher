#!/usr/bin/env bash
# Runs e2e/run.sh split across the free test emulators (emulator-5554, emulator-5556; each has its own lock), waiting
# while all are busy. run.sh's PLAN balances the sections by their last measured times; the Result lines are summed.
# Usage: [ONLY=…] [SHARDS=n] e2e/shard.sh   (SHARDS: at most n emulators; e2e/any.sh is SHARDS=1)
# Exit: the worst run.sh exit (0 all passed). Each shard's whole log: e2e/out/<serial>/run.log
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
lock() { echo "/tmp/ganimede-e2e-$1.lock"; } # the file lib.sh locks (fd 9) for the whole run: one lock per device
free=()
while [ ${#free[@]} -eq 0 ]; do
  for s in emulator-5554 emulator-5556; do
    [ ${#free[@]} -lt "${SHARDS:-2}" ] || break
    adb -s "$s" get-state >/dev/null 2>&1 && flock -n "$(lock "$s")" true && free+=("$s") # running and not busy
  done
  [ ${#free[@]} -gt 0 ] || sleep 5
done
mapfile -t plan < <(PLAN=${#free[@]} bash "$HERE/run.sh")
[ ${#plan[@]} -gt 0 ] || exit 2 # run.sh said why
trap 'trap - INT TERM; kill 0' INT TERM # every shard's run.sh puts its device back on the way out
start=$SECONDS; pids=()
for i in "${!plan[@]}"; do
  s=${free[i]}; mkdir -p "$HERE/out/$s"; echo "[$s] ONLY=${plan[i]}"
  # run.sh's lib.sh waits for the lock if another run took the emulator since the check above: the shard runs late, never not at all
  ( set -o pipefail; ONLY="${plan[i]}" bash "$HERE/run.sh" "$s" 2>&1 | tee "$HERE/out/$s/run.log" | sed -u "s/^/[$s] /" ) &
  pids+=($!)
done
rc=0; pass=0; fail=0
for i in "${!pids[@]}"; do
  wait "${pids[i]}"; r=$?; [ $r -gt $rc ] && rc=$r
  s=${free[i]}; res=$(grep -E '^Result: [0-9]+ passed, [0-9]+ failed' "$HERE/out/$s/run.log")
  if [ -z "$res" ]; then echo "[$s] no Result line: the shard stopped early (exit $r)"; [ $rc -eq 0 ] && rc=1; continue; fi
  read -r p f <<<"$(echo "$res" | grep -oE '[0-9]+' | head -2 | tr '\n' ' ')"; pass=$((pass + p)); fail=$((fail + f))
done
echo; echo "Result: $pass passed, $fail failed. ${#pids[@]} shard(s) on ${free[*]}, $((SECONDS - start)) s. Logs in $HERE/out/<serial>/run.log"
exit $rc
