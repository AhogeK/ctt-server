#!/usr/bin/env bash
#
# Unit tests for scripts/lib/install-functions.sh. No network, no containers,
# no mocking: every case is a function call on a fixture the test writes itself.
# Run directly (scripts/tests/install-functions-test.sh) or from
# .github/workflows/installer-test.yml.
#
# The cases are chosen around the mistakes that actually happened or nearly
# happened: guessing an asset name from one rule (cosign and sops format theirs
# differently), prefix-matching a checksum line (the SBOM line follows the
# binary and shares its prefix), and treating "v2.6.0" as different from
# "2.6.0" because a binary prints the bare number.

set -uo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
# shellcheck source=scripts/lib/install-functions.sh
. "$script_dir/lib/install-functions.sh"

failures=0
checks=0

pass() { checks=$((checks + 1)); }
fail_case() {
  checks=$((checks + 1))
  failures=$((failures + 1))
  printf 'FAIL  %s\n' "$1" >&2
  printf '      %s\n' "$2" >&2
}

assert_eq() {
  local name=$1 expected=$2 actual=$3
  if [ "$expected" = "$actual" ]; then pass; else fail_case "$name" "expected [$expected], got [$actual]"; fi
}

# Any non-zero exit counts, except 127: that means the shell could not find the
# command, so the case asserted nothing. It has happened — a case wrapped its
# subject in `bash -c`, where the function was undefined, and the suite reported
# a pass for a call that never ran.
assert_fails() {
  local name=$1 status=0
  shift
  "$@" >/dev/null 2>&1 || status=$?
  if [ "$status" -eq 0 ]; then
    fail_case "$name" 'expected a non-zero exit, got 0'
  elif [ "$status" -eq 127 ]; then
    fail_case "$name" 'exited 127 (command not found): the case did not run its subject'
  else
    pass
  fi
}

fixture=$(mktemp -d)
trap 'rm -rf "$fixture"' EXIT

# --- platform mapping ------------------------------------------------------

assert_eq 'uname Linux maps to linux' linux "$(detect_os Linux)"
assert_eq 'uname Darwin maps to darwin' darwin "$(detect_os Darwin)"
assert_fails 'an unknown operating system is refused' detect_os FreeBSD

assert_eq 'x86_64 maps to amd64' amd64 "$(detect_arch x86_64)"
assert_eq 'amd64 maps to amd64' amd64 "$(detect_arch amd64)"
assert_eq 'arm64 maps to arm64' arm64 "$(detect_arch arm64)"
assert_eq 'aarch64 maps to arm64' arm64 "$(detect_arch aarch64)"
assert_fails 'an unknown architecture is refused' detect_arch i686

# --- release asset names ---------------------------------------------------
# Taken from the published release manifests; the two projects do not agree.

assert_eq 'cosign on darwin/arm64' 'cosign-darwin-arm64' "$(asset_name cosign v2.6.0 darwin arm64)"
assert_eq 'cosign on linux/amd64' 'cosign-linux-amd64' "$(asset_name cosign v2.6.0 linux amd64)"
assert_eq 'sops on darwin/arm64' 'sops-v3.13.3.darwin.arm64' "$(asset_name sops v3.13.3 darwin arm64)"
assert_eq 'sops on linux/amd64' 'sops-v3.13.3.linux.amd64' "$(asset_name sops v3.13.3 linux amd64)"
assert_eq 'sops accepts a version without the v' 'sops-v3.13.3.linux.arm64' "$(asset_name sops 3.13.3 linux arm64)"
assert_fails 'an unknown tool has no asset' asset_name kubectl v1.0 linux amd64

assert_eq 'cosign checksums file' 'cosign_checksums.txt' "$(checksums_name cosign v2.6.0)"
assert_eq 'sops checksums file' 'sops-v3.13.3.checksums.txt' "$(checksums_name sops v3.13.3)"
assert_fails 'an unknown tool has no checksums file' checksums_name kubectl v1.0

assert_eq 'cosign lives in sigstore/cosign' 'sigstore/cosign' "$(tool_repository cosign)"
assert_eq 'sops lives in getsops/sops' 'getsops/sops' "$(tool_repository sops)"
assert_fails 'an unknown tool has no repository' tool_repository kubectl

# The pins are what operators get by default; a silent bump here would move
# every deployment without a reviewed change.
assert_eq 'the pinned cosign is v2.6.0' 'v2.6.0' "$(tool_default_version cosign)"
assert_eq 'the pinned sops is v3.13.3' 'v3.13.3' "$(tool_default_version sops)"
assert_fails 'an unknown tool has no pinned version' tool_default_version kubectl

assert_eq 'release url' \
  'https://github.com/sigstore/cosign/releases/download/v2.6.0/cosign-darwin-arm64' \
  "$(release_url sigstore/cosign v2.6.0 cosign-darwin-arm64)"

# --- checksum selection ----------------------------------------------------

# The real shape of cosign_checksums.txt: the binary, then an SBOM artefact
# whose name starts with the binary's name.
cat >"$fixture/cosign_checksums.txt" <<'EOF'
dea5b83b8b375b99ac803c7bdb1f798963dbeb47789ceb72153202e7f20e8d07  cosign-darwin-arm64
c09a84869eb31fcf334e54d0a9f81bf466ba7444dc975a8fe46b94d742288980  cosign-darwin-arm64_2.6.0_darwin_arm64.sbom.json
ea5c65f99425d6cfbb5c4b5de5dac035f14d09131c1a0ea7c7fc32eab39364f9  cosign-linux-amd64
EOF
assert_eq 'the SBOM line beside the binary does not win' \
  'dea5b83b8b375b99ac803c7bdb1f798963dbeb47789ceb72153202e7f20e8d07' \
  "$(pick_checksum "$fixture/cosign_checksums.txt" cosign-darwin-arm64)"
assert_eq 'the linux line is found independently' \
  'ea5c65f99425d6cfbb5c4b5de5dac035f14d09131c1a0ea7c7fc32eab39364f9' \
  "$(pick_checksum "$fixture/cosign_checksums.txt" cosign-linux-amd64)"

printf '  %s  cosign-darwin-arm64\n' 'aaa' >"$fixture/duplicate.txt"
printf '  %s  cosign-darwin-arm64\n' 'bbb' >>"$fixture/duplicate.txt"
assert_fails 'two entries for one asset is an error, not a coin flip' \
  pick_checksum "$fixture/duplicate.txt" cosign-darwin-arm64
assert_fails 'a missing asset is an error' \
  pick_checksum "$fixture/cosign_checksums.txt" cosign-linux-arm64

# A file that lists only the SBOM artefact must not satisfy a request for the
# binary: matching on "starts with" would return it.
printf '  %s  cosign-darwin-arm64_2.6.0_darwin_arm64.sbom.json\n' 'ccc' >"$fixture/sbom-only.txt"
assert_fails 'a longer name does not satisfy the shorter one' \
  pick_checksum "$fixture/sbom-only.txt" cosign-darwin-arm64

# --- version comparison ----------------------------------------------------

assert_eq 'normalise keeps a bare version' '2.6.0' "$(normalize_version 2.6.0)"
assert_eq 'normalise strips one leading v' '2.6.0' "$(normalize_version v2.6.0)"
if same_version v2.6.0 2.6.0; then pass; else fail_case 'v2.6.0 equals 2.6.0' 'the comparison disagreed'; fi
if same_version 2.6.0 2.6.0; then pass; else fail_case '2.6.0 equals itself' 'the comparison disagreed'; fi
if same_version v2.6.0 2.6.1; then fail_case 'different patch levels differ' 'the comparison agreed'; else pass; fi
if same_version v2.6.0 v3.0.0; then fail_case 'different major levels differ' 'the comparison agreed'; else pass; fi


# --- version commands and output parsing ------------------------------------
# Both fixtures are the real output shapes, captured from the binaries this
# script installs (cosign v2.6.0, sops v3.13.3 on darwin/arm64). cosign refuses
# `--version` and prints a banner before GitVersion; sops answers `--version`
# with the version line and then prose on stdout.

assert_eq 'cosign takes a subcommand' 'version' "$(version_command cosign)"
assert_eq 'sops takes a flag' '--version' "$(version_command sops)"
assert_fails 'an unknown tool has no version command' version_command kubectl

cat >"$fixture/cosign-version.txt" <<'EOF'
  ______   ______        _______. __    _______ .__   __.
 /      | /  __  \      /       ||  |  /  _____||  \ |  |
 \______| \______/  |_______/    |__|  \______| |__| \__|
cosign: A tool for Container Signing, Verification and Storage in an OCI registry.

GitVersion:    v2.6.0
GitCommit:     f5f7c0d0b0e0e0e0e0e0e0e0e0e0e0e0e0e0e0e0
GitTreeState:  clean
BuildDate:     2025-09-12T19:49:02Z
GoVersion:     go1.25.0
Compiler:      gc
Platform:      darwin/arm64
EOF
assert_eq 'the GitVersion line wins over the banner and the Go version' \
  '2.6.0' "$(parse_version <"$fixture/cosign-version.txt")"

# Written with printf rather than a heredoc: a line that starts with a bare
# bracket is easy to mangle, and this fixture has to keep its exact shape.
printf '%s\n' \
  'sops 3.13.3 (latest)' \
  '' \
  '  warning: sops will stop checking for updates when printing its version.' \
  >"$fixture/sops-version.txt"
assert_eq 'sops parses even with trailing prose on stdout' \
  '3.13.3' "$(parse_version <"$fixture/sops-version.txt")"

printf 'no version here\n' >"$fixture/no-version.txt"
assert_fails 'output without a dotted triple is a failure, not a guess' \
  parse_version <"$fixture/no-version.txt"

# --- summary ---------------------------------------------------------------

printf '\n%d checks, %d failure(s)\n' "$checks" "$failures"
[ "$failures" -eq 0 ]
