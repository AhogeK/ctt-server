#!/usr/bin/env bash
# Replace one colour of the staging stack and move traffic to it.
#
# The implementation is shared with the local stack and reads its ports, Compose
# files and Prometheus from `deploy/staging/stack.env` (P3.6); this file only
# names the stack.
set -euo pipefail

cd "$(dirname "$0")/../.."
export CTT_STACK_FILE=deploy/staging/stack.env
exec ./deploy/lib/release.sh "$@"
