#!/usr/bin/env bash
# Replace one colour and move traffic to it, with an observation window that can
# send traffic back (P3.4).
#
#   ./deploy/local/release.sh green                  # the local stack
#   ./deploy/local/on-engine.sh ./deploy/staging/release.sh green --no-jar
#                                                    # staging, where the probes are
#
# One implementation for both stacks (P3.6): the ports, the Compose files and
# the Prometheus it reads come from the stack file named in `CTT_STACK_FILE`.
#
# The colour being replaced is the one *not* serving, so the new build starts
# next to the live one and the switch itself is a reload (see switch-colour.sh).
# Nobody is served by a container that is still starting.
#
# The window is not decoration. Five minutes is long enough for a scrape, a
# token refresh and one connection pool cycle to happen against the new colour;
# the release fails — and returns traffic — on the first sample that shows a
# probe failure, a 5xx, or a p95 above the 300 ms contract. A rollback command
# exists because the other colour is still running with the previous image; it
# is a switch, not a deploy.
set -euo pipefail

cd "$(dirname "$0")/../.."

. "$(dirname "$0")/load-stack.sh"
read -r -a compose <<<"docker compose $CTT_COMPOSE_FILES"
prometheus=$CTT_PROMETHEUS_URL
edge=http://127.0.0.1:$CTT_EDGE_PUBLIC_PORT
observe=300
target=""
rollback=0
no_build=0
no_jar=0

while [ $# -gt 0 ]; do
  case "$1" in
    --observe) observe=${2:?--observe needs a number of seconds}; shift 2 ;;
    --rollback) rollback=1; shift ;;
    --no-build) no_build=1; shift ;;
    --no-jar) no_jar=1; shift ;;
    blue|green) target=$1; shift ;;
    *) echo "usage: $0 blue|green [--observe SECONDS] [--no-build] [--no-jar] | --rollback [--observe SECONDS]" >&2; exit 2 ;;
  esac
done

current="$("$(dirname "$0")/switch-colour.sh" | awk '{print $1}')"
case "$current" in
  blue|green) ;;
  *) echo "cannot tell which colour serves now: $current" >&2; exit 1 ;;
esac

if [ "$rollback" -eq 1 ]; then
  if [ "$current" = blue ]; then target=green; else target=blue; fi
  echo "rollback: $current → $target"
elif [ -z "$target" ]; then
  echo "usage: $0 blue|green [--observe SECONDS] [--no-build] [--no-jar] | --rollback [--observe SECONDS]" >&2
  exit 2
elif [ "$target" = "$current" ]; then
  echo "$target is already serving; release the other colour and switch to it" >&2
  exit 2
fi

query() {
  # `sed -n ... p` prints nothing and still exits 0 when the series has no
  # sample; a pipeline ending in grep would fail the script under `pipefail`
  # and turn "no data yet" into a crash. `|| true` keeps a Prometheus that
  # stopped answering from aborting the window: an empty answer is read as
  # "no data", which ends the release the way a breach does.
  curl -sf -G "$prometheus/api/v1/query" --data-urlencode "query=$1" 2>/dev/null \
    | sed -n 's/.*"value":\[[0-9.]*,"\([^"]*\)"\].*/\1/p' \
    | head -1 \
    || true
}

if ! curl -sf "$prometheus/-/ready" >/dev/null; then
  echo "Prometheus is not answering on $prometheus; the observation window needs the probes." >&2
  echo "Start the stack first: ./deploy/${stack_name}/up.sh" >&2
  exit 1
fi

if [ "$rollback" -eq 1 ]; then
  echo "[1/4] rollback: no build, the previous image is still what this colour runs"
  echo "[2/4] nothing to replace"
  echo "[3/4] $target has been running its image since before the last release"
elif [ "$no_build" -eq 1 ]; then
  echo "[1/4] no build: the image this stack already has"
  echo "[2/4] replacing $target"
  "${compose[@]}" --profile bluegreen up -d --no-build --no-deps "app-$target"
  echo "[3/4] waiting for $target to be ready"
else
  # Two builds, and only one of them needs this machine: `stage-jar.sh` runs the
  # Gradle wrapper (JDK, network), while `compose up --build` copies the staged
  # jar into an image (the engine). A rehearsal runs on the engine, where the
  # probes work, so `--no-jar` keeps the jar the host built and still rebuilds the
  # image — which is what makes the release a release.
  if [ "$no_jar" -eq 0 ]; then
    echo "[1/4] building the jar"
    "$(dirname "$0")/stage-jar.sh"
  else
    echo "[1/4] keeping the jar staged on the host"
  fi
  echo "[2/4] replacing $target"
  "${compose[@]}" --profile bluegreen up -d --build --no-deps "app-$target"
  echo "[3/4] waiting for $target to be ready"
fi

if [ "$rollback" -eq 0 ]; then
  # --no-deps above: postgres and redis keep running. Recreating the data plane
  # in the middle of a release would turn a code change into an outage of its own.
  management="$(management_of "$target")"
  deadline=$((SECONDS + 180))
  until curl -sf "http://127.0.0.1:$management/actuator/health/readiness" >/dev/null 2>&1; do
    if [ "$SECONDS" -ge "$deadline" ]; then
      echo "$target never became ready; $current is still serving" >&2
      exit 1
    fi
    sleep 3
  done

  # One request through the application port, before waiting on Prometheus. The
  # `http_server_requests` timer is registered by Micrometer on the first
  # recorded request, so a colour that has just started has no series at all —
  # and the edge is still pointing at the other colour, so nothing else will
  # call it. The management port does not count: its endpoints are not timed.
  port="$(port_of "$target")"
  curl -sf -o /dev/null "http://127.0.0.1:$port/ctt-server/api/v1/config/public" || true
fi

# The window computes rates from the target slot, and a rate needs two scrapes:
# a container that started seconds ago has one sample, so the first window
# sample would read as "no data" and fail a release that is in fact fine. Wait
# for the series here instead of relaxing the emptiness check inside the window.
series_deadline=$((SECONDS + 120))
until [ -n "$(query "$(printf 'count(rate(http_server_requests_seconds_count{job="ctt-server",slot="%s"}[1m]))' "$target")")" ]; do
  if [ "$SECONDS" -ge "$series_deadline" ]; then
    echo "$target has no rateable metrics in Prometheus; the window cannot judge it" >&2
    exit 1
  fi
  sleep 5
done

echo "[4/4] switching and watching for ${observe}s"
"$(dirname "$0")/switch-colour.sh" "$target"

breach=""
measured=0
elapsed=0
while [ "$elapsed" -lt "$observe" ]; do
  sleep 10
  elapsed=$((elapsed + 10))

  # Synthetic traffic is part of the observation: without requests the 5xx and
  # latency series have no samples, and an empty result would read as healthy.
  status="$(curl -s -o /dev/null -w '%{http_code}' "$edge/ctt-server/api/v1/config/public" || echo 000)"
  case "$status" in
    2??) ;;
    *) breach="edge answered $status to a probe request" ;;
  esac

  probe="$(query "$(printf 'max(probe_success{job="%s"})' "$CTT_PROBE_JOB")")"
  if [ -z "$probe" ]; then
    breach="probe_success has no data: the public probe is not reporting"
  elif [ "$probe" != 1 ]; then
    breach="probe_success is $probe, the public path is failing"
  fi

  # `or vector(0)`: summing a counter with no 5xx series yet yields an empty
  # vector, which is the healthy case, not missing data. With this default an
  # empty answer really does mean the query did not run.
  #
  # The slot filter is what makes the window judge the colour that serves:
  # both slots are scraped, so the idle one keeps contributing samples, and
  # the 5xx it was serving before the switch would judge the new colour.
  fivexx="$(query "$(printf 'sum(rate(http_server_requests_seconds_count{job="ctt-server",slot="%s",status=~"5.."}[1m])) or vector(0)' "$target")")"
  if [ -z "$fivexx" ]; then
    breach="the 5xx rate has no data: the application series are missing"
  elif [ "$(awk -v v="$fivexx" 'BEGIN { print (v > 0) }')" = 1 ]; then
    breach="the application is answering 5xx at ${fivexx}/s"
  fi

  # A quantile over too few observations is NaN or missing entirely; neither
  # proves anything about latency, and treating NaN as zero is how a gate goes
  # quietly green. Count what was actually measured, and refuse to call the
  # window clean when the contract was never measured.
  p95="$(query "$(printf 'histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{job="ctt-server",slot="%s"}[1m])))' "$target")")"
  if [ -z "$p95" ] || [ "$p95" = NaN ]; then
    p95_label=n/a
  else
    measured=$((measured + 1))
    p95_label="${p95}s"
    if [ "$(awk -v v="$p95" 'BEGIN { print (v > 0.3) }')" = 1 ]; then
      breach="p95 is ${p95}s, above the 300 ms contract"
    fi
  fi

  if [ -n "$breach" ]; then
    echo "  ${elapsed}s: $breach" >&2
    if [ "$rollback" -eq 1 ]; then
      # Both colours fail the contract now. The colour we rolled back to runs
      # the older image, and putting traffic back on the build we rolled away
      # from would undo the rollback, so traffic stays where it is.
      echo "  traffic stays on $target: rolling back to it did not make it healthy" >&2
      echo "  fix forward: both colours breach the window" >&2
    else
      echo "  returning traffic to $current" >&2
      "$(dirname "$0")/switch-colour.sh" "$current" >&2 || true
    fi
    exit 1
  fi
  echo "  ${elapsed}s: probe_success=$probe 5xx=$fivexx/s p95=$p95_label http=$status"
done

if [ "$measured" -eq 0 ]; then
  echo "$target is serving, but the window never measured p95: only the probe and the 5xx rate were judged" >&2
  echo "observe longer, or check the release by hand before trusting it" >&2
  exit 1
fi

echo "$target is serving: ${observe}s with ${measured} p95 samples, no probe failure and no 5xx"
if [ "$rollback" -eq 1 ]; then
  echo "release forward with: ./deploy/${stack_name}/release.sh $current"
  echo "roll back with: ./deploy/${stack_name}/release.sh --rollback"
fi
