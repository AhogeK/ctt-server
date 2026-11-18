#!/usr/bin/env bash
# Replace one colour of the local stack and move traffic to it.
#
# The implementation is shared with staging and reads its ports, Compose files
# and Prometheus from `deploy/local/stack.env` (P3.6); this file only names the
# stack.
set -euo pipefail

cd "$(dirname "$0")/../.."
export CTT_STACK_FILE=deploy/local/stack.env
exec ./deploy/lib/release.sh "$@"
