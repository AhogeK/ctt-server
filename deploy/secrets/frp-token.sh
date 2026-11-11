#!/usr/bin/env bash
# Render the frp tunnel token from the SOPS payload onto a host.
#
#   ./deploy/secrets/frp-token.sh [--remote HOST] [TARGET]
#
# TARGET defaults to /etc/frp/frp-token. The file is written with mode 600 —
# on this machine, or over SSH when --remote is given, so the SOPS identity
# never has to live on the host that runs frp. The token lives in
# deploy/secrets/secrets.enc.yaml under FRP_TOKEN, so it never enters a tracked
# frp config, shell history, `docker inspect` or a file other users can read.
set -euo pipefail

cd "$(dirname "$0")/../.."

enc="deploy/secrets/secrets.enc.yaml"
remote=""
target=""

usage() {
  cat <<'EOF'
usage: deploy/secrets/frp-token.sh [--remote HOST] [TARGET]

TARGET defaults to /etc/frp/frp-token and is written with mode 600.
EOF
}

while [ $# -gt 0 ]; do
  case "$1" in
    --remote)
      if [ $# -lt 2 ] || [ -z "$2" ]; then
        echo "--remote needs a host" >&2
        exit 1
      fi
      remote="$2"
      shift 2
      ;;
    -h|--help) usage; exit 0 ;;
    -*) echo "unknown argument: $1" >&2; exit 1 ;;
    *) target="$1"; shift ;;
  esac
done

target="${target:-/etc/frp/frp-token}"
case "$target" in
  *[!A-Za-z0-9/._-]*) echo "unsupported characters in target: $target" >&2; exit 1 ;;
esac

if [ ! -f "$enc" ]; then
  echo "missing $enc" >&2
  exit 1
fi

export SOPS_AGE_KEY_FILE="${SOPS_AGE_KEY_FILE:-$HOME/.config/sops/age/keys.txt}"
if [ ! -f "$SOPS_AGE_KEY_FILE" ]; then
  echo "missing age identity at $SOPS_AGE_KEY_FILE" >&2
  exit 1
fi
# sops reports "key not found" and "cannot decrypt" with its own diagnostics;
# keep them instead of collapsing both into a single "not in the payload".
if ! token="$(sops --decrypt --extract '["FRP_TOKEN"]' "$enc")"; then
  echo "could not read FRP_TOKEN from $enc" >&2
  exit 1
fi
if [ -z "$token" ]; then
  echo "FRP_TOKEN is empty in $enc" >&2
  exit 1
fi

dir="$(dirname "$target")"
size="$(printf '%s\n' "$token" | wc -c | tr -d ' ')"

assert_mode() {
  case "$1" in
    600) echo "wrote $2 (mode 600)" ;;
    *) echo "unexpected permissions on $2: ${1:-none}" >&2; exit 1 ;;
  esac
}

# mktemp creates the file mode 600, so a leftover next to the target cannot
# carry looser permissions into the rename.
if [ -n "$remote" ]; then
  # BatchMode keeps a host-key or passphrase prompt from consuming the token
  # that is piped in on stdin.
  if ! observed="$(printf '%s\n' "$token" | ssh -o BatchMode=yes -- "$remote" "
    set -eu
    umask 077
    mkdir -p -- '$dir'
    tmp=\$(mktemp '$dir/.frp-token.XXXXXX')
    trap 'rm -f \"\$tmp\"' EXIT
    cat > \"\$tmp\"
    chmod 600 \"\$tmp\"
    mv -f \"\$tmp\" '$target'
    trap - EXIT
    stat -c '%a %s' '$target' 2>/dev/null || stat -f '%Lp %z' '$target'
  ")"; then
    echo "could not write $remote:$target" >&2
    exit 1
  fi
  mode="${observed%% *}"
  written="${observed#* }"
  if [ "$written" != "$size" ]; then
    echo "$remote:$target holds $written bytes, expected $size" >&2
    exit 1
  fi
  assert_mode "$mode" "$remote:$target"
else
  umask 077
  mkdir -p -- "$dir"
  tmp="$(mktemp "$dir/.frp-token.XXXXXX")"
  trap 'rm -f "$tmp"' EXIT
  cat > "$tmp" <<<"$token"
  chmod 600 "$tmp"
  mv -f "$tmp" "$target"
  trap - EXIT
  assert_mode "$(stat -c '%a' "$target" 2>/dev/null || stat -f '%Lp' "$target")" "$target"
fi
