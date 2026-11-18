#!/usr/bin/env bash
# Switch the staging edge between colours.
#
# The implementation is shared with the local stack and reads its ports,
# container names and upstream directory from `deploy/staging/stack.env`
# (P3.6); this file only names the stack.
set -euo pipefail

cd "$(dirname "$0")/../.."
export CTT_STACK_FILE=deploy/staging/stack.env
exec ./deploy/lib/switch-colour.sh "$@"
