#!/usr/bin/env bash
# Replace one colour and move traffic to it, with an observation window that can
# send traffic back (P3.4).
#
#   ./deploy/local/release.sh green                  # build, replace green, switch, watch 5 min
#   ./deploy/local/release.sh green --no-build        # same, with the image as it is
#   ./deploy/local/release.sh green --observe 60      # a shorter window, for a rehearsal
#   ./deploy/local/release.sh --rollback              # back to the other colour, same window
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

compose=(docker compose -f docker-compose.yaml -f docker-compose.local.yaml)
prometheus=http://127.0.0.1:9090
edge=http://127.0.0.1:18080
observe=300
target=""
rollback=0
no_build=0

while [ $# -gt 0 ]; do
  case "$1" in
    --observe) observe=${2:?--observe needs a number of seconds}; shift 2 ;;
    --rollback) rollback=1; shift ;;
    --no-build) no_build=1; shift ;;
    blue|green) target=$1; shift ;;
    *) echo "usage: $0 blue|green [--observe SECONDS] [--no-build] | --rollback [--observe SECONDS]" >&2; exit 2 ;;
  esac
done

management_of() {
  case "$1" in
    blue) echo 8081 ;;
    green) echo 8082 ;;
    *) return 1 ;;
  esac
}

current="$(./deploy/local/switch-colour.sh | awk '{print $1}')"
case "$current" in
  blue|green) ;;
  *) echo "cannot tell which colour serves now: $current" >&2; exit 1 ;;
esac

if [ "$rollback" -eq 1 ]; then
  if [ "$current" = blue ]; then target=green; else target=blue; fi
  echo "rollback: $current → $target"
elif [ -z "$target" ]; then
  echo "usage: $0 blue|green [--observe SECONDS] [--no-build] | --rollback [--observe SECONDS]" >&2
  exit 2
elif [ "$target" = "$current" ]; then
  echo "$target is already serving; release the other colour and switch to it" >&2
  exit 2
fi

query() {
  # `sed -n ... p` prints nothing and still exits 0 when the series has no
  # sample; a pipeline ending in grep would fail the script under `pipefail`
  # and turn "no data yet" into a crash.
  curl -sf -G "$prometheus/api/v1/query" --data-urlencode "query=$1" 2>/dev/null \
    | sed -n 's/.*"value":\[[0-9.]*,"\([^"]*\)"\].*/\1/p' \
    | head -1
}

if ! curl -sf "$prometheus/-/ready" >/dev/null; then
  echo "Prometheus is not answering on $prometheus; the observation window needs the probes." >&2
  echo "Start the stack with: ./deploy/local/up.sh --profile bluegreen --profile obs" >&2
  exit 1
fi

if [ "$rollback" -eq 1 ]; then
  echo "[1/4] rollback: no build, the previous image is still what this colour runs"
  echo "[2/4] nothing to replace"
  echo "[3/4] $target readiness"
elif [ "$no_build" -eq 1 ]; then
  echo "[1/4] no build: the image this stack already has"
  echo "[2/4] replacing $target"
  "${compose[@]}" --profile bluegreen up -d --no-build --no-deps "app-$target"
  echo "[3/4] waiting for $target to be ready"
else
  echo "[1/4] building the jar"
  ./deploy/local/stage-jar.sh
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
fi

echo "[4/4] switching and watching for ${observe}s"
./deploy/local/switch-colour.sh "$target"

breach=""
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

  probe="$(query 'max(probe_success{job="blackbox-public"})')"
  if [ -z "$probe" ]; then
    breach="probe_success has no data: the public probe is not reporting"
  elif [ "$probe" != 1 ]; then
    breach="probe_success is $probe, the public path is failing"
  fi

  # `or vector(0)`: summing a counter with no 5xx series yet yields an empty
  # vector, which is the healthy case, not missing data. With this default an
  # empty answer really does mean the query did not run.
  fivexx="$(query 'sum(rate(http_server_requests_seconds_count{job="ctt-server",status=~"5.."}[1m])) or vector(0)')"
  if [ -z "$fivexx" ]; then
    breach="the 5xx rate has no data: the application series are missing"
  elif [ "$(awk -v v="$fivexx" 'BEGIN { print (v > 0) }')" = 1 ]; then
    breach="the application is answering 5xx at ${fivexx}/s"
  fi

  p95="$(query 'histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{job="ctt-server"}[1m])))')"
  if [ -z "$p95" ]; then
    breach="p95 has no data: the histogram has no samples in the window"
  elif [ "$(awk -v v="$p95" 'BEGIN { print (v > 0.3) }')" = 1 ]; then
    breach="p95 is ${p95}s, above the 300 ms contract"
  fi

  if [ -n "$breach" ]; then
    echo "  ${elapsed}s: $breach" >&2
    echo "  returning traffic to $current" >&2
    ./deploy/local/switch-colour.sh "$current" >&2 || true
    exit 1
  fi
  echo "  ${elapsed}s: probe_success=$probe 5xx=$fivexx/s p95=${p95}s http=$status"
done

echo "$target is serving: ${observe}s without a probe failure, a 5xx or a p95 above 300 ms"
echo "roll back with: ./deploy/local/release.sh --rollback"
