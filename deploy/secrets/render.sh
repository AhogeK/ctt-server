#!/usr/bin/env bash
# Render the SOPS-encrypted secrets into the files Compose mounts (P0.5).
#
#   ./deploy/secrets/render.sh [--out DIR] [--mode 0440]
#
# The encrypted payload is the only copy in the repository; this script writes
# one file per key under deploy/secrets/run/ (gitignored). Compose reads those
# files as secrets, so no secret value appears in `docker inspect`.
#
# Decryption happens in a temporary directory first: a wrong age identity or a
# corrupt payload must never destroy the last good rendered set.
set -euo pipefail

cd "$(dirname "$0")/../.."

enc="deploy/secrets/secrets.enc.yaml"
out="deploy/secrets/run"
mode="440"
while [ $# -gt 0 ]; do
  case "$1" in
    --out) out="${2:?--out needs a directory}"; shift 2 ;;
    --mode) mode="${2:?--mode needs an octal mode}"; shift 2 ;;
    *) echo "unknown argument: $1" >&2; exit 1 ;;
  esac
done
group="${SECRET_GID:-1000}"
case "$out" in
  deploy/secrets/*) ;;
  *) echo "refusing to render outside deploy/secrets/: $out" >&2; exit 1 ;;
esac

if [ ! -f "$enc" ]; then
  echo "missing $enc; run this script from a checkout that has the encrypted payload" >&2
  exit 1
fi

export SOPS_AGE_KEY_FILE="${SOPS_AGE_KEY_FILE:-$HOME/.config/sops/age/keys.txt}"
if [ ! -f "$SOPS_AGE_KEY_FILE" ]; then
  echo "missing age identity at $SOPS_AGE_KEY_FILE" >&2
  exit 1
fi

umask 077
tmp="$(mktemp -d "${TMPDIR:-/tmp}/ctt-secrets.XXXXXX")"
trap 'rm -rf "$tmp"' EXIT

# Decrypt to a file first: a failed decryption keeps its own error message
# instead of a parser traceback, and exits before touching the live set.
sops --decrypt --output-type json "$enc" > "$tmp/secrets.json"
mkdir -p "$tmp/rendered"
python3 -c '
import json, os, pathlib, sys

plaintext = pathlib.Path(sys.argv[1])
out = pathlib.Path(sys.argv[2])
data = json.loads(plaintext.read_text())
written = 0
for key, value in data.items():
    if value is None or str(value) == "":
        print(f"skipped empty secret: {key}", file=sys.stderr)
        continue
    path = out / key
    path.write_text(str(value))
    os.chmod(path, int(sys.argv[3], 8))
    written += 1
print(f"decrypted {written} secret files", file=sys.stderr)
' "$tmp/secrets.json" "$tmp/rendered" "$mode"

# Assign the group before replacing the live set: a failed chgrp must leave
# the previous set intact (the same rule as decryption failures).
find "$tmp/rendered" -maxdepth 1 -type f -exec chgrp "$group" {} +
mkdir -p "$out"
# Only now that decryption and group assignment succeeded: replace the
# previous set so a secret removed from the payload cannot keep working.
find "$out" -maxdepth 1 -type f -delete
cp -a "$tmp/rendered"/. "$out"/
echo "secrets ready in $out with mode $mode and group $group"
