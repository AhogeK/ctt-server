#!/usr/bin/env bash
#
# One end-to-end sequence against scripts/install.sh: a cold install, a second
# run that must be a no-op, `--verify-only`, a release that must be refused
# without leaking a temp directory, and the notice printed when the prefix
# already holds a different version.
#
# It lives in a file rather than a string so the host and the matrix containers
# run the same sequence. They used to carry two copies, and the container copy
# installed one tool while the host installed both — the probe that mattered for
# "does this work on another libc" was quietly the weaker one.
#
# Usage: scripts/tests/install-probe.sh [all|cosign|sops]   (default: all)

set -euo pipefail

tool=${1:-all}
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)

case "$tool" in
  all | cosign | sops) ;;
  *)
    echo "usage: install-probe.sh [all|cosign|sops]" >&2
    exit 1
    ;;
esac

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
cp -r "$script_dir/scripts" "${work}/scripts"
cd "$work"

# The installer cleans up through a process-wide trap, so a leaked directory is
# the observable failure of that trap; count before and after the failing run.
tmp_count() {
  find "${TMPDIR:-/tmp}" -maxdepth 1 -type d -name 'tmp.*' 2>/dev/null | wc -l | tr -d ' '
}

echo "--- cold install (${tool}) ---"
./scripts/install.sh --prefix "${work}/bin" --tool "$tool"

echo "--- second run is a no-op ---"
second=$(./scripts/install.sh --prefix "${work}/bin" --tool "$tool")
case "$second" in
  *"already at"*) ;;
  *)
    echo "FAIL: second run was not a no-op: $second"
    exit 1
    ;;
esac

echo "--- verify-only downloads nothing ---"
./scripts/install.sh --prefix "${work}/bin" --tool "$tool" --verify-only

echo "--- a release that does not exist fails and leaves no temp directory ---"
before=$(tmp_count)
if ./scripts/install.sh --prefix "${work}/bin" --tool cosign --version v0.0.0-nope 2>/dev/null; then
  echo "FAIL: a missing release was accepted"
  exit 1
fi
after=$(tmp_count)
if [ "$before" != "$after" ]; then
  echo "FAIL: temp dirs ${before} -> ${after}"
  exit 1
fi

echo "--- a binary that does not report the pinned version exits 2 ---"
broken="${work}/broken"
mkdir -p "$broken"
printf '#!/bin/sh\necho "no version here"\n' >"${broken}/cosign"
chmod +x "${broken}/cosign"
status=0
./scripts/install.sh --prefix "$broken" --tool cosign --verify-only >/dev/null 2>&1 || status=$?
if [ "$status" -ne 2 ]; then
  echo "FAIL: a mismatch must exit 2, got ${status}"
  exit 1
fi

echo "--- an older binary in the prefix is announced, not silently swapped ---"
mkdir -p "${work}/bin"
printf '#!/bin/sh\necho "cosign: v1.0.0"\n' >"${work}/bin/cosign"
chmod +x "${work}/bin/cosign"
notice=$(./scripts/install.sh --prefix "${work}/bin" --tool cosign --version v0.0.0-nope 2>&1 || true)
case "$notice" in
  *"replacing 1.0.0 with the pinned v0.0.0-nope"*) ;;
  *)
    echo "FAIL: the replacement was not announced: $notice"
    exit 1
    ;;
esac

echo "--- library unit tests ---"
./scripts/tests/install-functions-test.sh

echo "PASS: probe ($tool)"
