#!/usr/bin/env bash
# Replace one production colour and move traffic to it.
#
# The implementation is shared with the local and staging stacks and reads its
# ports, Compose files, edge container and Prometheus from
# `deploy/prod/stack.env` (P3.6/P3.8); this file only names the stack.
#
# A release pulls and runs the digest the deploy job passed with `--image`, so
# the colour it replaces runs exactly the bytes the publish workflow signed.
#
#   ./deploy/prod/release.sh --next --image ghcr.io/ahogek/ctt-server@sha256:…
#   ./deploy/prod/release.sh --rollback
#   ./deploy/prod/release.sh --next --first-release --image …   # nothing serving yet
set -euo pipefail

cd "$(dirname "$0")/../.."
export CTT_STACK_FILE=deploy/prod/stack.env
exec ./deploy/lib/release.sh "$@"
