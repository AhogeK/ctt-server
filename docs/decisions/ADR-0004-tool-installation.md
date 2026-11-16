# ADR-0004: Install the verification tools with a repository script, not a general-purpose installer

**Date:** 2026-11-16 · **Status:** Accepted

## Context

Two binaries must exist on the deployment host, on a developer's machine, and inside the
deployment container: `cosign` (verifies the image signature and provenance attestation) and `sops`
(decrypts the secret payloads during a render). Until now the runbook said "download it from the
release page", and every failure of that instruction was silent:

- **Asset names are not one rule.** `cosign-darwin-arm64` carries no version; `sops-v3.13.3.linux.amd64`
  repeats it and uses dots. A browser save of a wrong name stores an HTML error page.
- **The checksums file mixes derived artefacts.** `cosign-darwin-arm64` is followed by
  `cosign-darwin-arm64_2.6.0_darwin_arm64.sbom.json`, so a prefix match returns the SBOM digest.
- **The version flags disagree.** `cosign` rejects `--version` and prints an ASCII banner before
  `GitVersion:`; `sops` answers `--version` and then writes a deprecation notice to stdout.

Two of these were found only by running the downloads for real (2026-11-16).

## Decision

Install both tools with `scripts/install.sh`, which:

- keeps the per-tool facts — repository, pinned version, asset name, checksums file name, version
  command — in one library (`scripts/lib/install-functions.sh`), unit-tested without a network;
- verifies the download against the checksums file the project itself publishes;
- stages the binary beside the destination and `mv`s it over the old one, because `install` and `cp`
  truncate in place and an interrupted copy leaves a broken binary where a working one was;
- proves the result by running the binary's own version command and exits `2` when it disagrees;
- never uses `sudo`, and installs into `~/.local/bin` (or `--prefix`).

Every consumer in the repository uses it: CI's sops step, `deploy/verify-image.sh`'s missing-tool
message, and the Jenkins tooling preflight.

## Alternatives Considered

| Option | Why not |
| --- | --- |
| Keep the manual download instructions | The three silent failures above; there is no evidence an operator following the page got the right file |
| `aqua` | Verifies checksums and attestations, but is itself a bootstrap binary plus an `aqua.yaml`; for two tools in a deploy path it adds a runtime and a config format that must be kept in sync with the pins |
| `mise` | Same shape (`.mise.toml`, its own install step, GitHub backend). Its value is managing many language runtimes; this repository needs two binaries |
| asdf | Plugins and a shell shim; slower, and no checksum verification of its own |
| `eget` | One tool, one invocation per binary, unpinned unless scripted — that script is the script we wrote, with fewer checks |
| `sigstore/cosign-installer` (GitHub Action) | CI only; the deployment host and a developer's machine are not GitHub runners |
| `curl … | bash` from each project | Unpinned, no checksum, and the two projects' scripts differ; a partial download is installed happily |

## Consequences

**Positive.** One command, one pin, one place to add a third tool (the library table); the failure
modes are asserted by tests rather than remembered; the same sequence runs on macOS, Debian, Ubuntu
and Alpine, so the libc-specific parts (`sha256sum` vs `shasum`, GNU vs busybox) are exercised.

**Negative.** The repository now owns ~200 lines of installer plus its tests; a new tool means
editing the table and its unit test. The pins must be revisited deliberately — a version bump is a
reviewed change with a verification run behind it, not an implicit upgrade.

**Neutral.** The checksum proves the bytes are what the project published for that release; it does
not prove the release is authentic. Both projects sign their checksums files, and verifying that
requires `cosign`, which is the binary being installed — the script documents this boundary instead
of pretending the digest is a signature.

## Revisit triggers

- A third or fourth binary is needed, or the pins must track releases automatically (then a
  general-purpose installer, or Renovate plus the existing script, becomes worth its cost).
- A supported platform appears that has neither `sha256sum` nor `shasum`.
- Either project changes its asset naming or version output — the unit tests fail first.
