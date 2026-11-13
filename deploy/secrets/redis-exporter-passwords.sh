#!/usr/bin/env bash
#
# Render the redis_exporter password map from the SOPS payload.
#
# The exporter authenticates from a JSON map of URI to password
# (`-redis.password-file`, see exporter.LoadPwdFile in the upstream project), and
# its image has no shell to build that map from a plain secret. Rendering the map
# here keeps the password out of container environment variables, out of
# `docker inspect`, and out of the process arguments.
#
# The file is written 0444: the exporter runs as an unprivileged uid inside its
# container, and the mount is the only path that uid has to the value.

set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
payload="${script_dir}/secrets.enc.yaml"

usage() {
  cat <<'EOF'
Usage: ./deploy/secrets/redis-exporter-passwords.sh [--remote HOST] [--addr URI] [TARGET]

  --remote HOST  render on HOST over SSH instead of locally
  --addr URI     Redis URI the map is keyed by (default redis://127.0.0.1:6379)
  TARGET         output file; required with --remote (the remote host has no
                 repository path to default to), default deploy/secrets/run/…
EOF
}

remote=""
addr="redis://127.0.0.1:6379"
target=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --remote)
      if [[ $# -lt 2 || -z "${2:-}" ]]; then
        echo "--remote needs a host" >&2
        exit 1
      fi
      remote="$2"
      shift 2
      ;;
    --addr)
      if [[ $# -lt 2 || -z "${2:-}" ]]; then
        echo "--addr needs a URI" >&2
        exit 1
      fi
      addr="$2"
      shift 2
      ;;
    -h | --help)
      usage
      exit 0
      ;;
    -*)
      echo "unknown option: $1" >&2
      exit 1
      ;;
    *)
      target="$1"
      shift
      ;;
  esac
done

if [[ -z "$target" ]]; then
  if [[ -n "$remote" ]]; then
    echo "--remote needs a target path on that host" >&2
    exit 1
  fi
  target="${script_dir}/run/redis-exporter-passwords.json"
fi

case "$addr" in
  *[!A-Za-z0-9:/._-]*)
    echo "refusing address with unexpected characters: $addr" >&2
    exit 1
    ;;
esac

if [[ ! -f "$payload" ]]; then
  echo "missing payload: $payload" >&2
  exit 1
fi

if [[ -z "${SOPS_AGE_KEY_FILE:-}" && -f "$HOME/.config/sops/age/keys.txt" ]]; then
  export SOPS_AGE_KEY_FILE="$HOME/.config/sops/age/keys.txt"
fi

if ! password="$(sops --decrypt --extract '["REDIS_PASSWORD"]' "$payload")"; then
  echo "could not read REDIS_PASSWORD from $payload" >&2
  exit 1
fi

if [[ -z "$password" ]]; then
  echo "REDIS_PASSWORD is empty in $payload" >&2
  exit 1
fi

# The map is JSON, so the only characters that cannot appear literally are the
# quote, the backslash and control characters. Anything else round-trips.
case "$password" in
  *'"'* | *'\'*)
    echo "refusing password containing a quote or backslash" >&2
    exit 1
    ;;
esac

if printf '%s' "$password" | LC_ALL=C grep -q '[[:cntrl:]]'; then
  echo "refusing password containing control characters" >&2
  exit 1
fi

map="$(printf '{"%s":"%s"}\n' "$addr" "$password")"
size="$(printf '%s' "$map" | wc -c | tr -d ' ')"
dir="$(dirname -- "$target")"

assert_mode() {
  case "$1" in
    444) echo "wrote $2 (mode 444, ${size} bytes)" ;;
    *)
      echo "unexpected permissions on $2: ${1:-none}" >&2
      exit 1
      ;;
  esac
}

if [[ -n "$remote" ]]; then
  remote_dir="$(dirname -- "$target")"
  remote_name="$(basename -- "$target")"
  # The remote half is one shell command: write beside the target, rename, read
  # the result back. Backslash-newline continues the string, so the spaces that
  # follow are harmless separators inside it.
  if ! observed="$(printf '%s' "$map" | ssh -o BatchMode=yes -- "$remote" \
    "set -eu; umask 077; mkdir -p -- '$remote_dir'; cd -- '$remote_dir'; \
    tmp=\$(mktemp .redis-exporter.XXXXXX); trap 'rm -f \"\$tmp\"' EXIT; \
    cat > \"\$tmp\"; chmod 444 \"\$tmp\"; mv -f \"\$tmp\" '$remote_name'; \
    trap - EXIT; stat -c '%a %s' '$remote_name'")"; then
    echo "could not write $remote:$target" >&2
    exit 1
  fi
  mode="${observed%% *}"
  written="${observed#* }"
  if [[ "$written" != "$size" ]]; then
    echo "$remote:$target holds $written bytes, expected $size" >&2
    exit 1
  fi
  assert_mode "$mode" "$remote:$target"
  exit 0
fi

mkdir -p -- "$dir"
tmp="$(mktemp "$dir/.redis-exporter.XXXXXX")"
trap 'rm -f "$tmp"' EXIT
printf '%s' "$map" > "$tmp"
chmod 444 "$tmp"
mv -f -- "$tmp" "$target"
trap - EXIT

written="$(wc -c < "$target" | tr -d ' ')"
if [[ "$written" != "$size" ]]; then
  echo "$target holds $written bytes, expected $size" >&2
  exit 1
fi
assert_mode "$(stat -c '%a' "$target" 2>/dev/null || stat -f '%Lp' "$target")" "$target"
