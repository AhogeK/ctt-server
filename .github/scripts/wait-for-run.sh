#!/usr/bin/env bash
# Waits for one workflow run and prints a verdict the caller can trust.
#
# Two failure modes this closes, both hit for real on 2026-11-16:
#
#   gh run watch <id> --exit-status | tail -6
#
# The exit status of that pipeline belongs to `tail`, so without `pipefail` a red
# run reads as green; and the six lines you see are the last steps, not the
# conclusion. A verdict is three facts together: the watch finished, the run's
# status is `completed`, and its conclusion is `success`. This asks the API for
# the last two and returns an exit code matching the verdict.
#
# Usage: wait-for-run.sh <run-id>
set -euo pipefail

run=${1:?usage: wait-for-run.sh <run-id>}

watched=0
gh run watch "$run" --exit-status --interval 10 > /dev/null 2>&1 || watched=$?

read -r status conclusion attempt < <(
  gh run view "$run" --json status,conclusion,attempt --jq '"\(.status) \(.conclusion) \(.attempt)"'
)

printf 'run %s attempt %s status %s conclusion %s\n' \
  "$run" "$attempt" "$status" "${conclusion:-none}"

if [ "$status" = completed ] && [ "$conclusion" = success ] && [ "$watched" -eq 0 ]; then
  echo "VERDICT: green"
  exit 0
fi

echo "VERDICT: RED"
exit 1
