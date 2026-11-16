#!/usr/bin/env bash
#
# Exercise scripts/install.sh across operating systems that the author's
# machine is not: Debian, Ubuntu-slim and Alpine in containers, plus the host
# itself. Each target runs the same sequence from scripts/tests/install-probe.sh
# — cold install of both tools, a no-op second run, `--verify-only`, a release
# that must be refused without leaking a temp directory, and the notice printed
# when the prefix already holds a different version — followed by the unit tests.
#
# This is not proof that the installer works on a bare machine: the container
# still inherits the host kernel, and a "clean" container is not the same as a
# freshly provisioned server with a different libc and a proxy in front of
# GitHub. It does prove the parts that depend on the OS family — `sha256sum`
# versus `shasum`, GNU versus busybox userland, asset selection from `uname`.
#
# Usage: scripts/test-install-matrix.sh [target ...]
#   targets: host debian ubuntu alpine   (default: all four)

set -euo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
repo_root=$(cd -- "$script_dir/.." && pwd)

targets=("$@")
[ ${#targets[@]} -eq 0 ] && targets=(host debian ubuntu alpine)

run_inside() {
  local label=$1 image=$2 prepare=$3
  printf '\n=== %s (%s) ===\n' "$label" "$image"
  docker run --rm \
    -v "$repo_root:/repo:ro" \
    "$image" \
    sh -c "$prepare
set -eu
bash /repo/scripts/tests/install-probe.sh all
echo \"PASS: $label\""
}

case_target() {
  case "$1" in
    host)
      printf '\n=== host (%s %s) ===\n' "$(uname -s)" "$(uname -m)"
      "$repo_root/scripts/tests/install-probe.sh" all
      ;;
    debian)
      run_inside debian debian:12-slim '
        apt-get update -qq >/dev/null && apt-get install -y -qq --no-install-recommends curl ca-certificates tar >/dev/null'
      ;;
    ubuntu)
      run_inside ubuntu ubuntu:24.04 '
        apt-get update -qq >/dev/null && apt-get install -y -qq --no-install-recommends curl ca-certificates tar >/dev/null'
      ;;
    alpine)
      run_inside alpine alpine:3.20 '
        apk add --no-cache bash curl tar >/dev/null'
      ;;
    *)
      printf 'unknown target: %s\n' "$1" >&2
      exit 1
      ;;
  esac
}

for target in "${targets[@]}"; do
  case_target "$target"
done

printf '\nall requested targets passed\n'
