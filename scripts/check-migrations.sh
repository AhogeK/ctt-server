#!/usr/bin/env bash
#
# Refuses the two migration mistakes that no test catches before a release.
#
# Editing or deleting a migration the released branch already applied: Flyway
# records a checksum per applied migration, so a mismatch stops startup on every
# environment that ran the old file, while an environment created afterwards
# silently gets a different schema than production. Reverting the edit is the
# only correct repair, so the gate fails at the edit instead of at deployment.
#
# Shipping a destructive statement while the previous version still serves
# traffic: a blue-green cutover runs the migration with the old colour still
# answering requests, and a column it still selects, a constraint it still
# writes against, or a type it still compares becomes a 500 in that window. The
# statement is legal later, once the old version is gone, so such a file must
# say so in a `-- contract:` comment — that makes the delay visible in review
# instead of implied by nobody noticing.
#
# The behavioural half — two versions actually running against one schema — is
# scripts/migration-coexistence-drill.sh. This script is the cheap static gate
# that can run on every change.
#
# Usage: scripts/check-migrations.sh [--base <revision>]
#   --base   released revision to compare against (default: origin/master, then master)
#   --dir    migration directory (default: src/main/resources/db/migration)
# Prints MIGRATIONS: ok | MIGRATIONS: RED; exits non-zero on red.
set -uo pipefail

cd "$(dirname "$0")/.."
root=$(git rev-parse --show-toplevel)
cd "$root"

base=""
dir=src/main/resources/db/migration
while [ $# -gt 0 ]; do
  case "$1" in
    --base)
      base=${2:?--base needs a revision}
      shift 2
      ;;
    --dir)
      dir=${2:?--dir needs a path}
      shift 2
      ;;
    -h | --help)
      sed -n '3,26p' "$0" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    *)
      printf 'unknown argument: %s\n' "$1" >&2
      exit 2
      ;;
  esac
done

if [ -z "$base" ]; then
  for candidate in origin/master master; do
    if git rev-parse --verify --quiet "$candidate" >/dev/null 2>&1; then
      base=$candidate
      break
    fi
  done
fi

problems=()
warn() { printf 'note: %s\n' "$*"; }
fail() { problems+=("$*"); }

if [ -n "$base" ] && ! git rev-parse --verify --quiet "$base^{commit}" >/dev/null; then
  printf '  %s: does not resolve, so the release comparison cannot run; fetch it or pass --base <revision>\n' "$base"
  printf 'MIGRATIONS: RED (1 problem(s))\n'
  exit 1
fi

# --- files on disk -----------------------------------------------------------
if [ ! -d "$dir" ]; then
  printf '  %s: not a directory, so there is nothing to check\n' "$dir"
  printf 'MIGRATIONS: RED (1 problem(s))\n'
  exit 1
fi

files=()
while IFS= read -r file; do
  files+=("$file")
done < <(find "$dir" -maxdepth 1 -type f -name 'V*.sql' -print | sort)

if [ "${#files[@]}" -eq 0 ]; then
  printf '  %s: holds no V*.sql migration, so nothing was checked\n' "$dir"
  printf 'MIGRATIONS: RED (1 problem(s))\n'
  exit 1
fi

# --- naming and version uniqueness ------------------------------------------
names=()
for file in "${files[@]}"; do
  name=${file#"$dir"/}
  if [[ ! $name =~ ^V([0-9]{14})__([a-z][a-z0-9_]*)(\.[a-z][a-z0-9_]*)?\.sql$ ]]; then
    fail "$name: name must be V<14-digit UTC timestamp>__<lower_snake_case>.sql"
    continue
  fi
  names+=("$name")
done

# An associative array says this in one loop, but stock macOS ships bash 3.2,
# and a gate that dies on the reviewer's shell has checked nothing.
if [ "${#names[@]}" -gt 0 ]; then
  while read -r version first rest; do
    fail "$rest: version $version is already used by $first"
  done < <(printf '%s\n' "${names[@]}" | sort |
    awk -F'__' '{ if ($1 in seen) print $1, seen[$1], $0; else seen[$1] = $0 }')
fi

# --- history is immutable ----------------------------------------------------
if [ -n "$base" ]; then
  while IFS= read -r path; do
    [ -n "$path" ] || continue
    name=${path#"$dir"/}
    if [ ! -f "$path" ]; then
      fail "$name: removed, but $base already applied it; Flyway cannot unapply a migration"
      continue
    fi
    if [ "$(git rev-parse "$base:$path" 2>/dev/null)" != "$(git hash-object "$path")" ]; then
      fail "$name: edited after $base applied it; add a new migration instead"
    fi
  done < <(git ls-tree -r --name-only "$base" -- "$dir" 2>/dev/null | grep '\.sql$' || true)
else
  warn "no released revision found (origin/master, master); only naming and contract checks ran"
  [ -z "${CI:-}" ] || fail "no released revision found; the immutability check did not run"
fi

# --- destructive statements need their contract step to be stated ------------
# Matched against the statement text with comments removed and newlines
# flattened, so neither a comment that mentions a keyword nor a statement
# wrapped over two lines decides the verdict; the acknowledgement itself is a
# comment, which is the point.
# Index creation is absent on purpose: it blocks writes while building (use
# CONCURRENTLY) but leaves the running version working, and flagging it would
# teach authors to write the acknowledgement without meaning it.
# Each entry is `regex|label`: the regex decides the verdict, the label is what
# the author reads. PostgreSQL makes COLUMN optional in DROP and ALTER, so the
# short forms matter — they are the ones an author writes by hand.
patterns=(
  'DROP TABLE|DROP TABLE'
  'ALTER TABLE[^;]* DROP [A-Z_][A-Z0-9_]*|DROP COLUMN or DROP CONSTRAINT (COLUMN is optional)'
  'ALTER TABLE[^;]* ALTER [A-Z_][A-Z0-9_]*[^;]* TYPE|ALTER COLUMN ... TYPE (COLUMN is optional)'
  'SET NOT NULL|SET NOT NULL'
  'RENAME TO|RENAME TO'
  'RENAME COLUMN|RENAME COLUMN'
  'DROP VIEW|DROP VIEW'
  'DROP FUNCTION|DROP FUNCTION'
  'TRUNCATE|TRUNCATE'
  'DELETE FROM|DELETE FROM'
)

for file in "${files[@]}"; do
  path=${file#"$dir"/}
  if [ -n "$base" ] && git cat-file -e "$base:$file" 2>/dev/null; then
    continue # already released; immutability covers it
  fi
  body=$(sed 's/--.*$//' "$file" | tr '\n' ' ' |
    sed 's:/\*[^*]*\*\{1,\}\([^/*][^*]*\*\{1,\}\)*/: :g' |
    tr '[:lower:]' '[:upper:]')
  hits=()
  for entry in "${patterns[@]}"; do
    if grep -Eq "${entry%%|*}" <<<"$body"; then
      hits+=("${entry#*|}")
    fi
  done
  if grep -Eq 'ADD COLUMN[^;]*NOT NULL' <<<"$body" &&
    ! grep -Eq 'ADD COLUMN[^;]*NOT NULL[^;]*DEFAULT' <<<"$body"; then
    hits+=('ADD COLUMN ... NOT NULL without DEFAULT')
  fi
  if [ "${#hits[@]}" -gt 0 ] && ! grep -Eqi -- '--[[:space:]]*contract:' "$file"; then
    joined=$(printf '%s; ' "${hits[@]}")
    fail "$path: ${joined%; } — the running version must survive this, so add a '-- contract:' comment naming when it lands"
  fi
done

# --- verdict -----------------------------------------------------------------
if [ "${#problems[@]}" -gt 0 ]; then
  for problem in "${problems[@]}"; do
    printf '  %s\n' "$problem"
  done
  printf 'MIGRATIONS: RED (%d problem(s) across %d file(s))\n' "${#problems[@]}" "${#files[@]}"
  exit 1
fi

new_count=0
if [ -n "$base" ]; then
  for file in "${files[@]}"; do
    git cat-file -e "$base:$file" 2>/dev/null || new_count=$((new_count + 1))
  done
fi
printf 'MIGRATIONS: ok (%d file(s), %d new against %s)\n' "${#files[@]}" "$new_count" "${base:-none}"
