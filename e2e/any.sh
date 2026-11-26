#!/usr/bin/env bash
# Runs e2e/run.sh on the first free test emulator, one e2e at a time per emulator, waiting while both are busy.
# Usage: [ONLY=…] e2e/any.sh   (the emulators: emulator-5554 and emulator-5556; each has its own lock)
HERE="$(cd "$(dirname "$0")" && pwd)"
lock() { [ "$1" = emulator-5554 ] && echo /tmp/ganimede-emu.lock || echo "/tmp/ganimede-emu-${1#emulator-}.lock"; }
while :; do
  for s in emulator-5554 emulator-5556; do
    adb -s "$s" get-state >/dev/null 2>&1 || continue # that emulator is not running
    flock -n -E 75 "$(lock "$s")" bash "$HERE/run.sh" "$s"; rc=$?
    [ $rc -ne 75 ] && exit $rc # 75: busy, try the other one
  done
  sleep 5
done
