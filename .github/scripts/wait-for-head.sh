#!/usr/bin/env bash
# Blocks until the run for the current HEAD has a verdict, then prints it.
#
# A push does not publish its run instantly: for the first seconds `gh run list
# --limit 1` still answers with the previous run, whose green says nothing about
# the commit just sent — that is how a red push was once read as green. Resolving
# by the exact SHA removes the window, and reusing wait-for-run.sh keeps one
# definition of what a green verdict is.
#
# Usage: wait-for-head.sh [branch]   (default: the current branch)
set -euo pipefail

branch=${1:-$(git rev-parse --abbrev-ref HEAD)}
sha=$(git rev-parse HEAD)

run=""
for _ in $(seq 1 36); do
  run=$(gh run list --branch "$branch" --limit 20 \
    --json databaseId,headSha \
    --jq "[.[] | select(.headSha == \"$sha\")][0].databaseId")
  [ -n "$run" ] && break
  sleep 10
done

if [ -z "$run" ]; then
  echo "no workflow run for $sha on $branch after 6 minutes" >&2
  echo "VERDICT: RED (no run)"
  exit 1
fi

echo "run $run is for ${sha:0:7} on $branch"
exec "$(dirname "$0")/wait-for-run.sh" "$run"
