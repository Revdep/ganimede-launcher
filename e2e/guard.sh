#!/usr/bin/env bash
# Guard over the e2e sections (run.sh calls it before touching a device; CI runs it with the syntax check). Each helper below
# exists because the plain command lost a race, and each of those races made a run red for no reason:
#   typ        not `input text`: keys sent before the keyboard is up, or reordered by Gboard, type "cla" for "cal" / "por pro"
#   put_prefs  not `exec-in`: exec-in returns once stdin is sent, so a rename after it ran late or never
#   edit_prefs not `sed -i`: it escapes & and keeps the way the app stores a set
#   wait_until not `sleep N; hasnt ...`: a negative check after a fixed sleep passes when the screen is merely late
# A line that really needs the plain command says so with "guard-ok: why" on the same line.
HERE="$(cd "$(dirname "$0")" && pwd)"
bad=0
check() { # check regex message
  local hits; hits=$(grep -nE -- "$1" "$HERE"/sections/*.sh | grep -v 'guard-ok:' | sed "s|$HERE/||")
  [ -z "$hits" ] || { echo "$hits" | sed "s|\$|  <- $2|"; bad=1; }
}
check 'input text' 'use typ (lib.sh)'
check 'exec-in' 'use put_prefs (lib.sh)'
check 'sed +(-[a-zA-Z]*i|--in-place)' 'use edit_prefs (lib.sh), or comment guard-ok'
check 'sleep[^;&|]*[;&] *(hasnt|noshows|! *has)' 'wait for a positive signal, then check the negative (wait_until)'
[ $bad = 0 ] || { echo "e2e guard: see e2e/guard.sh" >&2; exit 1; }
