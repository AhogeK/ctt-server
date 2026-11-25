#!/usr/bin/env bash
# Switch the production edge between colours.
#
# The implementation is shared with the local and staging stacks and reads its
# ports, container names and upstream directory from `deploy/prod/stack.env`
# (P3.6/P3.8); this file only names the stack.
#
# With no argument it prints the colour the edge is proxying to right now, which
# is how the deploy job decides which colour is idle.
set -euo pipefail

cd "$(dirname "$0")/../.."
export CTT_STACK_FILE=deploy/prod/stack.env
exec ./deploy/lib/switch-colour.sh "$@"
