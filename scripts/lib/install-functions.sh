#!/usr/bin/env bash
#
# Pure helpers behind scripts/install.sh. Nothing here touches the network or
# the filesystem (except reading the checksums file handed to it), so the
# mapping tables can be exercised by scripts/tests/install-functions-test.sh
# without downloading anything.
#
# The release asset names are not guessable from a single rule: cosign uses
# dashes and a version-less name, sops uses dots and repeats the version in the
# filename. Both facts were taken from the published release manifests
# (sigstore/cosign v2.6.0 and v3.1.4, getsops/sops v3.13.3) rather than inferred.
#
# The facts are separate functions rather than one associative table: a typo in
# a function name fails when the script is parsed and every caller is visible in
# `rg`, whereas a typo in a table key fails only when that tool is installed.

# The repositories and the pins sit beside the asset tables: adding or moving a
# tool is one edit here, not one here and three in install.sh. The pins are not
# defaults to be refreshed blindly — the deployment host verified cosign v2.6.0
# against a real digest, and v3 changed flags that deploy/verify-image.sh uses.
readonly COSIGN_REPOSITORY='sigstore/cosign'
readonly SOPS_REPOSITORY='getsops/sops'
readonly DEFAULT_COSIGN_VERSION='v2.6.0'
readonly DEFAULT_SOPS_VERSION='v3.13.3'

set -o pipefail

# detect_os <uname -s>  → linux | darwin
detect_os() {
  case "$1" in
    Linux) printf 'linux\n' ;;
    Darwin) printf 'darwin\n' ;;
    *)
      printf 'unsupported operating system: %s\n' "$1" >&2
      return 1
      ;;
  esac
}

# detect_arch <uname -m>  → amd64 | arm64
detect_arch() {
  case "$1" in
    x86_64 | amd64) printf 'amd64\n' ;;
    arm64 | aarch64) printf 'arm64\n' ;;
    *)
      printf 'unsupported architecture: %s\n' "$1" >&2
      return 1
      ;;
  esac
}

# asset_name <tool> <version> <os> <arch>
asset_name() {
  case "$1" in
    cosign) printf 'cosign-%s-%s\n' "$3" "$4" ;;
    sops) printf 'sops-v%s.%s.%s\n' "${2#v}" "$3" "$4" ;;
    *)
      printf 'unknown tool: %s\n' "$1" >&2
      return 1
      ;;
  esac
}

# checksums_name <tool> <version>
checksums_name() {
  case "$1" in
    cosign) printf 'cosign_checksums.txt\n' ;;
    sops) printf 'sops-v%s.checksums.txt\n' "${2#v}" ;;
    *)
      printf 'unknown tool: %s\n' "$1" >&2
      return 1
      ;;
  esac
}

# tool_repository <tool>  → the GitHub repository that publishes its releases
tool_repository() {
  case "$1" in
    cosign) printf '%s\n' "$COSIGN_REPOSITORY" ;;
    sops) printf '%s\n' "$SOPS_REPOSITORY" ;;
    *)
      printf 'unknown tool: %s\n' "$1" >&2
      return 1
      ;;
  esac
}

# tool_default_version <tool>  → the version this repository installs by default
tool_default_version() {
  case "$1" in
    cosign) printf '%s\n' "$DEFAULT_COSIGN_VERSION" ;;
    sops) printf '%s\n' "$DEFAULT_SOPS_VERSION" ;;
    *)
      printf 'unknown tool: %s\n' "$1" >&2
      return 1
      ;;
  esac
}

# version_command <tool>  → the argument that makes the binary print its version
#
# They do not agree, and guessing wrong is not cosmetic: cosign refuses
# `--version` outright ("unknown flag"), so an installer that assumes the common
# spelling reports a perfectly good install as broken.
version_command() {
  case "$1" in
    cosign) printf 'version\n' ;;
    sops) printf -- '--version\n' ;;
    *)
      printf 'unknown tool: %s\n' "$1" >&2
      return 1
      ;;
  esac
}

# parse_version  ← version output on stdin  → normalised version, or failure
#
# cosign prints an ASCII banner and a description before a `GitVersion:` line,
# so the first line is never the version; sops prints `sops 3.13.3 (latest)` and
# then a multi-line deprecation warning on stdout. Prefer the labelled line, then
# fall back to the first dotted triple anywhere in the output.
parse_version() {
  local output labelled
  output=$(cat)
  labelled=$(printf '%s\n' "$output" | sed -n 's/^[[:space:]]*GitVersion:[[:space:]]*//p' | head -1)
  if [ -n "$labelled" ]; then
    normalize_version "$labelled"
    return 0
  fi
  labelled=$(printf '%s\n' "$output" | grep -oE 'v?[0-9]+\.[0-9]+\.[0-9]+' | head -1)
  [ -n "$labelled" ] || return 1
  normalize_version "$labelled"
}

# pick_checksum <checksums-file> <asset>  → the digest for exactly that asset
#
# The published files list derived artefacts beside the binary —
# `cosign-darwin-arm64` is followed by `cosign-darwin-arm64_2.6.0_…sbom.json`,
# and a prefix match would happily return that one. Field two must equal the
# asset name exactly, and a file that yields zero or several matches is an
# error rather than a guess.
pick_checksum() {
  awk -v want="$2" '
    $2 == want { digest = $1; found++ }
    END {
      if (found == 1) { print digest; exit 0 }
      if (found == 0) { printf "no checksum entry for %s\n", want > "/dev/stderr"; exit 1 }
      printf "%d checksum entries for %s\n", found, want > "/dev/stderr"
      exit 1
    }
  ' "$1"
}

# normalize_version <version>  → version without a leading v
normalize_version() {
  printf '%s\n' "${1#v}"
}

# same_version <a> <b>  → exit 0 when both normalise to the same string
same_version() {
  [ "$(normalize_version "$1")" = "$(normalize_version "$2")" ]
}

# sha256_of <file>  → lowercase hex digest
#
# GNU coreutils ship `sha256sum`; macOS ships `shasum`, which needs the algorithm
# as an argument. Containers built from busybox have `sha256sum`.
sha256_of() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{ print $1 }'
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$1" | awk '{ print $1 }'
  else
    printf 'neither sha256sum nor shasum is available\n' >&2
    return 1
  fi
}

# release_url <repository> <version> <asset>
release_url() {
  printf 'https://github.com/%s/releases/download/%s/%s\n' "$1" "$2" "$3"
}
