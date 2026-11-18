#!/usr/bin/env bash
# Move the edge to one colour without dropping a request (P3.4).
#
#   ./deploy/local/switch-colour.sh blue      # the local stack
#   ./deploy/staging/switch-colour.sh green   # the staging stack
#   ./deploy/local/switch-colour.sh           # print the colour serving now
#
# This is the one implementation for both stacks (P3.6): everything that
# differs between them — ports, container names, the upstream directory — comes
# from the stack file the caller names in `CTT_STACK_FILE`, so a fix here fixes
# a rehearsal on either stack and the two cannot drift apart.
#
# The site block imports `edge-upstream/active.caddy`, a symlink to one of the
# two variants, and the edge keeps an admin endpoint on loopback for exactly one
# operation: `caddy reload`. The running process keeps serving its configuration
# until the replacement is accepted, so a switch costs no connection — and a
# configuration that fails to parse leaves the previous one in place.
#
# The target colour must be ready first. Switching to a colour that is still
# starting is how a rollout becomes an outage, so the readiness probe runs before
# anything is touched, and the reload is verified twice: against the
# configuration the edge actually holds, and by a request that has to cross it.
set -euo pipefail

cd "$(dirname "$0")/../.."

for tool in curl docker; do
  command -v "$tool" >/dev/null 2>&1 || {
    echo "$tool is not on PATH; nothing was switched" >&2
    exit 1
  }
done

. "$(dirname "$0")/load-stack.sh"

upstream_dir=$CTT_UPSTREAM_DIR
active="$upstream_dir/active.caddy"
edge_container=$CTT_EDGE_CONTAINER
admin_host=$CTT_EDGE_ADMIN
admin_url="http://$admin_host"

# What the edge is proxying to right now, read from its live configuration. An
# empty answer means the admin endpoint did not reply, which is a different
# problem from an unexpected upstream and must not be reported as one.
running_upstream() {
  curl -sf --max-time 5 "$admin_url/config/" 2>/dev/null \
    | grep -o '"dial":"[0-9.]*:[0-9]*"' \
    | head -1 \
    | cut -d'"' -f4 \
    || true
}

current_colour() {
  local target
  target="$(readlink "$active" 2>/dev/null || true)"
  case "$target" in
    blue.caddy) echo blue ;;
    green.caddy) echo green ;;
    *) echo unknown ;;
  esac
}

wait_ready() {
  local management=$1 deadline=$((SECONDS + 120))
  until curl -sf "http://127.0.0.1:$management/actuator/health/readiness" >/dev/null 2>&1; do
    if [ "$SECONDS" -ge "$deadline" ]; then
      return 1
    fi
    sleep 2
  done
}

reload() {
  docker exec "$edge_container" caddy reload \
    --config /etc/caddy/Caddyfile \
    --adapter caddyfile \
    --address "$admin_host"
}

if [ $# -eq 0 ]; then
  held="$(running_upstream)"
  echo "$(current_colour) (edge holds ${held:-nothing — the admin endpoint did not answer})"
  exit 0
fi

colour=$1
port="$(port_of "$colour")" || { echo "usage: $0 blue|green" >&2; exit 2; }
management="$(management_of "$colour")"

if ! wait_ready "$management"; then
  echo "$colour is not ready on 127.0.0.1:$management; not switching" >&2
  exit 1
fi

previous="$(current_colour)"

# A temporary name plus a rename, so the import never resolves to a half-written
# link. The edge has already loaded its configuration; this file matters at the
# next reload.
ln -sfn "$colour.caddy" "$active.new"
mv -f "$active.new" "$active"

if ! reload; then
  echo "the edge rejected the new configuration; it still serves $previous" >&2
  ln -sfn "$previous.caddy" "$active"
  exit 1
fi

held="$(running_upstream)"
if [ -z "$held" ]; then
  echo "reload accepted but the admin endpoint stopped answering; check $edge_container" >&2
  exit 1
fi
if [ "$held" != "127.0.0.1:$port" ]; then
  echo "reload accepted but the edge holds $held, expected 127.0.0.1:$port" >&2
  ln -sfn "$previous.caddy" "$active"
  reload || true
  exit 1
fi
# The configuration names the new colour; that is not the same as traffic
# arriving at it. A request through the edge is the only proof of the leg that
# matters — the colour being ready (checked above) and the edge forwarding where
# it says are two different failures, and only this one is visible to a caller.
# An application endpoint, not the management one: the application port serves
# no actuator payload (that is the two-port contract from P1.1), so /actuator/**
# answers 404 there and would report a healthy colour as unreachable.
probe_path=/ctt-server/api/v1/config/public
deadline=$((SECONDS + 15))
while ! curl -sf -o /dev/null "http://127.0.0.1:$CTT_EDGE_PUBLIC_PORT$probe_path"; do
  if [ "$SECONDS" -ge "$deadline" ]; then
    echo "the edge holds 127.0.0.1:$port but $probe_path does not succeed through it; returning to $previous" >&2
    ln -sfn "$previous.caddy" "$active"
    reload || true
    exit 1
  fi
  sleep 1
done

echo "edge → $colour (127.0.0.1:$port) · reload accepted · $probe_path answered · previous: $previous"
