#!/usr/bin/env bash
#
# Exercises scripts/check-migrations.sh against throwaway migration directories.
# The gate's dangerous failure is a green verdict while it examines nothing or
# looks at the wrong text, so every case here is a way it could lie.
#
# Run directly or from scripts/preflight.sh and the `Migration compatibility`
# job; it needs no Docker and no network.
set -uo pipefail

cd "$(dirname "$0")/../.."
gate=./scripts/check-migrations.sh
fixtures=$(mktemp -d)
failures=0

cleanup() { rm -rf "$fixtures"; }
trap cleanup EXIT

reset() {
  rm -rf "$fixtures/dir"
  mkdir -p "$fixtures/dir"
}

report() {
  printf '  %s: %s\n' "$1" "$2"
  failures=$((failures + 1))
}

# The gate is asked to compare against HEAD rather than a branch: the fixtures
# are not tracked, so immutability has nothing to say, and HEAD always resolves.
verdict() {
  "$gate" --base HEAD --dir "${2:-$fixtures/dir}" >"$fixtures/out" 2>&1
  printf '%s' "$?"
}

expect_green() {
  local label=$1 code
  code=$(verdict)
  [ "$code" -eq 0 ] || report "$label" "expected green, got RED: $(tail -1 "$fixtures/out")"
}

expect_red() {
  local label=$1 pattern=$2 code
  code=$(verdict)
  [ "$code" -ne 0 ] || report "$label" "expected RED, got green"
  grep -Fq "$pattern" "$fixtures/out" || report "$label" "RED without \"$pattern\""
}

reset
printf 'ALTER TABLE users ADD COLUMN nickname varchar(64);\n' >"$fixtures/dir/V20990101000001__add_nickname.sql"
expect_green "additive migration"

reset
printf 'ALTER TABLE users DROP COLUMN avatar_url;\n' >"$fixtures/dir/V20990101000001__drop.sql"
expect_red "drop column" "DROP COLUMN"

reset
printf 'ALTER TABLE users ADD COLUMN x int\n  NOT NULL;\n' >"$fixtures/dir/V20990101000001__wrapped.sql"
expect_red "statement wrapped over two lines" "NOT NULL without DEFAULT"

reset
printf '/* an earlier release considered DROP COLUMN avatar_url */\nSELECT 1;\n' >"$fixtures/dir/V20990101000001__commented.sql"
expect_green "keyword inside a block comment"

reset
printf -- '-- contract: after v0.121 nothing reads it\nALTER TABLE users DROP COLUMN avatar_url;\n' >"$fixtures/dir/V20990101000001__contracted.sql"
expect_green "acknowledged contract statement"

reset
printf 'SELECT 1;\n' >"$fixtures/dir/V20990101000001__first.sql"
printf 'SELECT 1;\n' >"$fixtures/dir/V20990101000001__second.sql"
expect_red "same version twice" "already used"

reset
printf 'ALTER TABLE users DROP avatar_url;\n' >"$fixtures/dir/V20990101000001__short.sql"
expect_red "drop column without the keyword" "DROP COLUMN"

reset
printf 'ALTER TABLE users ALTER avatar_url TYPE varchar(10);\n' >"$fixtures/dir/V20990101000001__narrow.sql"
expect_red "type change without the keyword" "ALTER COLUMN ... TYPE"

reset
printf 'SELECT 1;\n' >"$fixtures/dir/V2026__add_nickname.sql"
expect_red "name that Flyway would not order" "14-digit UTC timestamp"

reset
code=$(verdict "empty directory")
[ "$code" -ne 0 ] || report "empty directory" "expected RED, got green"
grep -Fq "nothing was checked" "$fixtures/out" || report "empty directory" "RED without \"nothing was checked\""

reset
printf 'SELECT 1;\n' >"$fixtures/dir/V20990101000001__ok.sql"
"$gate" --base no-such-revision --dir "$fixtures/dir" >"$fixtures/out" 2>&1
[ $? -ne 0 ] || report "unresolved base" "expected RED, got green"
grep -Fq "does not resolve" "$fixtures/out" || report "unresolved base" "RED without \"does not resolve\""

rm -rf "$fixtures/dir"
code=$(verdict "missing directory" "$fixtures/absent")
[ "$code" -ne 0 ] || report "missing directory" "expected RED, got green"
grep -Fq "not a directory" "$fixtures/out" || report "missing directory" "RED without \"not a directory\""

if [ "$failures" -gt 0 ]; then
  printf 'MIGRATIONS TEST: RED (%s case(s))\n' "$failures"
  exit 1
fi
printf 'MIGRATIONS TEST: green\n'
