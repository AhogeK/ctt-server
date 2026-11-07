#!/usr/bin/env bash
# Render the frp tunnel token from the SOPS payload onto the access machine.
#
#   ./deploy/secrets/frp-token.sh [TARGET]
#
# TARGET defaults to /etc/frp/frp-token and is written with mode 600. The token
# lives in deploy/secrets/secrets.enc.yaml under FRP_TOKEN, so it never enters a
# tracked frp config, shell history or `docker inspect`.
set -euo pipefail

cd "$(dirname "$0")/../.."

enc="deploy/secrets/secrets.enc.yaml"
target="${1:-/etc/frp/frp-token}"

if [ ! -f "$enc" ]; then
  echo "missing $enc" >&2
  exit 1
fi

export SOPS_AGE_KEY_FILE="${SOPS_AGE_KEY_FILE:-$HOME/.config/sops/age/keys.txt}"
if [ ! -f "$SOPS_AGE_KEY_FILE" ]; then
  echo "missing age identity at $SOPS_AGE_KEY_FILE" >&2
  exit 1
fi

token="$(sops --decrypt --extract '["FRP_TOKEN"]' "$enc" 2>/dev/null || true)"
if [ -z "$token" ]; then
  echo "FRP_TOKEN is not in $enc; add it with: sops $enc" >&2
  exit 1
fi

umask 077
mkdir -p "$(dirname "$target")"
printf '%s\n' "$token" > "$target"
chmod 600 "$target"
echo "wrote $target (mode 600)"
