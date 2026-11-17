#!/usr/bin/env bash
# Blocks until *every* workflow run for the current HEAD has a verdict.
#
# A push does not publish its runs instantly, and one commit can start several:
# on `master` a merge starts `CI` and `Publish`, and the failure notifier is a
# separate `workflow_run`. Waiting for the first one reported is how a red
# `Publish` was once read as a green delivery — `CI` was green, `Publish` had
# already died on a 429. So this waits for all of them, and keeps looking for
# late arrivals until nothing new has appeared or changed for a settle window.
# The deadline is a guard: a run stuck in `queued` (or a workflow that will
# never be created) must not leave the caller waiting forever.
#
# Usage: wait-for-head.sh [branch] [settle-seconds] [deadline-seconds]
#   branch defaults to the current branch, settle to 120 s, deadline to 1 hour.
set -euo pipefail

branch=${1:-$(git rev-parse --abbrev-ref HEAD)}
settle=${2:-120}
deadline=${3:-3600}
sha=$(git rev-parse HEAD)
short=${sha:0:7}
started=$(date +%s)

runs_for_sha() {
  gh run list --branch "$branch" --limit 40 \
    --json databaseId,name,event,status,conclusion,updatedAt,headSha \
    --jq "[.[] | select(.headSha == \"$sha\")]"
}

# Runs show up a few seconds after the push; give up if the commit never starts one.
first=""
for _ in $(seq 1 36); do
  first=$(runs_for_sha)
  [ "$(jq 'length' <<<"$first")" -gt 0 ] && break
  sleep 10
done
if [ -z "$first" ] || [ "$(jq 'length' <<<"$first")" = 0 ]; then
  echo "no workflow run for $short on $branch after 6 minutes" >&2
  echo "VERDICT: RED (no run)"
  exit 1
fi

seen='[]'
while :; do
  runs=$(runs_for_sha)
  # Keep the *latest* snapshot of each run: a run first seen as `in_progress`
  # would otherwise stay `in_progress` here forever, and the loop would wait
  # on a run that had already failed.
  seen=$(jq -s '.[0] + .[1] | group_by(.databaseId) | map(.[-1])' <<<"$seen"$'\n'"$runs")
  pending=$(jq -r '[.[] | select(.status != "completed") | "\(.name)[\(.event)]:\(.status)"] | join(", ")' <<<"$seen")
  now=$(date +%s)
  echo "[$((now - started))s] $(jq 'length' <<<"$seen") run(s)${pending:+, pending: $pending}"
  if [ -z "$pending" ]; then
    age=$(jq -r --argjson now "$now" \
      '(([.[].updatedAt | fromdateiso8601] | max) | ($now - .) | floor)' <<<"$seen")
    [ "$age" -ge "$settle" ] && break
  fi
  if [ $((now - started)) -ge "$deadline" ]; then
    echo "runs for $short on $branch did not settle within ${deadline}s:" >&2
    jq -r '.[] | "  \(.databaseId) \(.name) [\(.event)] \(.status)/\(.conclusion // "-")"' <<<"$seen" >&2
    echo "VERDICT: RED (deadline)"
    exit 2
  fi
  sleep 10
done

echo "runs for $short on $branch:"
jq -r '.[] | "  \(.databaseId) \(.name) [\(.event)] \(.status)/\(.conclusion // "-")"' <<<"$seen"

bad=$(jq -r '[.[] | select(.conclusion == "failure" or .conclusion == "timed_out"
  or .conclusion == "startup_failure" or .conclusion == "action_required")] | length' <<<"$seen")
if [ "$bad" = 0 ]; then
  echo "VERDICT: green"
  exit 0
fi
echo "VERDICT: RED ($bad run(s) failed)"
exit 1
