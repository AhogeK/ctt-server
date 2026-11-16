#!/usr/bin/env bash
#
# Install or upgrade the two binaries this repository's operational tooling
# needs — cosign (signature verification for published images) and sops
# (secret payload rendering) — into a per-user directory, without sudo.
#
# Why a script instead of "download the tarball": the deployment documentation
# tells an operator to install cosign from the release page by hand, and that is
# exactly where the last two incidents started. A release asset name that does
# not exist fails silently in a browser (you save an HTML error page), the
# architecture is guessed from the wrong `uname`, and the checksum is skipped
# because the file that carries it has a different name per project. This
# script resolves the name from a table, verifies the digest against the file
# the project publishes, installs atomically, and proves the result by running
# the binary's own `--version`.
#
# Usage:
#   scripts/install.sh                     # both tools, default versions
#   scripts/install.sh --tool cosign       # one tool
#   scripts/install.sh --version v2.6.0    # pin another release
#   scripts/install.sh --verify-only       # check what is installed, download nothing
#
# Exit codes: 0 success, 1 usage or platform error, 2 verification failure.

set -euo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=scripts/lib/install-functions.sh
. "$script_dir/lib/install-functions.sh"

tool=all
version=''
prefix="${HOME}/.local/bin"
verify_only=false

# Directories to remove when the process ends, however it ends. `fail` exits
# immediately, so a cleanup attached to the function's return would leak the
# half-downloaded file exactly when something went wrong — which is the case
# that matters. Verified by counting temp directories across a failed run.
cleanup_dirs=()
trap 'if [ ${#cleanup_dirs[@]} -gt 0 ]; then rm -rf -- "${cleanup_dirs[@]}"; fi' EXIT

usage() {
  # Print the leading comment block rather than a fixed line range: the header
  # grows whenever a decision needs explaining, and a stale range silently drops
  # the last paragraph of help.
  awk 'NR > 2 { if ($0 !~ /^#/) exit; sub(/^# ?/, ""); print }' "${BASH_SOURCE[0]}"
}

fail() {
  local message=$1 code=${2:-1}
  printf 'install.sh: %s\n' "$message" >&2
  exit "$code"
}

while [ $# -gt 0 ]; do
  case "$1" in
    --tool) tool="${2:?--tool needs a value}"; shift 2 ;;
    --version) version="${2:?--version needs a value}"; shift 2 ;;
    --prefix) prefix="${2:?--prefix needs a value}"; shift 2 ;;
    --verify-only) verify_only=true; shift ;;
    -h | --help) usage; exit 0 ;;
    *) fail "unknown argument: $1" ;;
  esac
done

case "$tool" in
  all | cosign | sops) ;;
  *) fail "--tool must be all, cosign or sops" ;;
esac

# One --version would be applied to both tools, and they do not share a version
# line: cosign is v2.6.0, sops is v3.13.3. Refusing is kinder than downloading a
# release that never existed.
if [ -n "$version" ] && [ "$tool" = all ]; then
  fail '--version needs --tool: cosign and sops do not share a version'
fi

os=$(detect_os "$(uname -s)") || fail "cannot map $(uname -s) to a release asset"
arch=$(detect_arch "$(uname -m)") || fail "cannot map $(uname -m) to a release asset"


# reported_version <tool> <binary>  → the version the binary reports, or nothing
#
# The flag differs per tool (see version_command) and the output is prose, so
# the parsing lives in the library where it is unit-tested against the real
# banner cosign prints.
reported_version() {
  local tool=$1 binary=$2 arg
  arg=$(version_command "$tool") || return 1
  "$binary" "$arg" 2>/dev/null | parse_version
}

verify_one() {
  local name=$1 wanted=$2 binary output
  binary="${prefix}/${name}"
  if [ ! -x "$binary" ]; then
    printf '%-7s missing  %s\n' "$name" "$binary"
    return 2
  fi
  output=$(reported_version "$name" "$binary") || output=''
  if same_version "$wanted" "$output"; then
    printf '%-7s ok       %s reports %s\n' "$name" "$binary" "$output"
    return 0
  fi
  printf '%-7s mismatch %s reports "%s", expected %s\n' "$name" "$binary" "$output" "$wanted"
  return 2
}

install_one() {
  local name=$1 wanted=$2 repository asset checksums_file asset_digest actual_digest
  local workdir target staged

  repository=$(tool_repository "$name") || fail "unknown tool: $name"
  asset=$(asset_name "$name" "$wanted" "$os" "$arch")
  checksums_file=$(checksums_name "$name" "$wanted")

  if [ -x "${prefix}/${name}" ] && same_version "$wanted" "$(reported_version "$name" "${prefix}/${name}")"; then
    printf '%-7s already at %s (%s), nothing to do\n' "$name" "$wanted" "${prefix}/${name}"
    return 0
  fi

  # Say what is about to be replaced. The common surprise is a binary put there
  # by another package manager: it is a different version, so the pin wins, and
  # the operator should not have to diff two versions to discover that.
  if [ -x "${prefix}/${name}" ]; then
    local present
    present=$(reported_version "$name" "${prefix}/${name}") || present='unknown'
    printf '%-7s replacing %s with the pinned %s\n' "$name" "$present" "$wanted"
  fi
  workdir=$(mktemp -d)
  # The download and the checksums file both live and die inside this directory:
  # a partial download must never be able to become the installed binary. The
  # removal is registered with the process-wide trap above, not with a RETURN
  # trap, because every failure path leaves through `exit`.
  cleanup_dirs+=("$workdir")

  printf '%-7s downloading %s for %s/%s\n' "$name" "$wanted" "$os" "$arch"
  curl --fail --silent --show-error --location \
    --output "${workdir}/${asset}" \
    "$(release_url "$repository" "$wanted" "$asset")" ||
    fail "download failed: $(release_url "$repository" "$wanted" "$asset")"
  curl --fail --silent --show-error --location \
    --output "${workdir}/${checksums_file}" \
    "$(release_url "$repository" "$wanted" "$checksums_file")" ||
    fail "checksums download failed: $checksums_file"

  asset_digest=$(pick_checksum "${workdir}/${checksums_file}" "$asset") ||
    fail "no usable checksum for ${asset} in ${checksums_file}"
  actual_digest=$(sha256_of "${workdir}/${asset}") || fail "cannot hash ${asset}"
  if [ "$asset_digest" != "$actual_digest" ]; then
    fail "checksum mismatch for ${asset}: published ${asset_digest}, downloaded ${actual_digest}"
  fi

  mkdir -p "$prefix"
  target="${prefix}/${name}"
  # Stage beside the destination and rename over it. `install` and `cp` truncate
  # the target in place, so an interrupted copy leaves a broken binary where the
  # working one used to be; `mv` inside one directory is an atomic rename, and
  # the staged name cannot equal `${name}`, so a leftover is visible.
  staged="${target}.new.$$"
  cp "${workdir}/${asset}" "$staged" || fail "cannot write $staged"
  chmod 0755 "$staged" || fail "cannot make $staged executable"
  mv -f "$staged" "$target" || fail "cannot install to $target"
  printf '%-7s installed %s (%s)\n' "$name" "$target" "$wanted"

  local output
  output=$(reported_version "$name" "$target") || output=''
  same_version "$wanted" "$output" ||
    fail "$target reports \"$output\" after installing $wanted" 2
}

status=0
for name in cosign sops; do
  case "$tool" in
    all) ;;
    "$name") ;;
    *) continue ;;
  esac
  wanted=${version:-$(tool_default_version "$name")} || fail "unknown tool: $name"
  if $verify_only; then
    verify_one "$name" "$wanted" || status=2
  else
    install_one "$name" "$wanted"
    verify_one "$name" "$wanted" || status=2
  fi
done


# The default prefix is not on PATH anywhere, and deploy/secrets/render.sh calls
# `sops` bare: without this line an operator installs correctly and the next
# command the runbook names fails with "command not found".
case ":${PATH}:" in
  *":${prefix}:"*) ;;
  *) printf 'note: %s is not on PATH. Add it with: export PATH="%s:%s"\n' "$prefix" "$prefix" "\$PATH" ;;
esac
exit "$status"
