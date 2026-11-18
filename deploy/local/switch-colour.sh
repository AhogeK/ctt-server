#!/usr/bin/env bash
# Switch the local stack's edge between colours.
#
# The implementation is shared with staging and reads its ports, container names
# and upstream directory from `deploy/local/stack.env` (P3.6); this file only
# names the stack.
set -euo pipefail

cd "$(dirname "$0")/../.."
export CTT_STACK_FILE=deploy/local/stack.env
exec ./deploy/lib/switch-colour.sh "$@"
