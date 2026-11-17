#!/usr/bin/env bash
#
# Runs locally what CI runs, before the change leaves the machine.
#
# Red pull requests that were avoidable: a formatting violation, an image build
# whose context lacked a file, a checksum set that only a warm Gradle home hid,
# and a dependency range that needs every repository to answer. All four fail on
# the machine that wrote them; CI is the backstop for the change, not the first
# reader of it.
#
# Scope follows the change, so a documentation commit does not wait minutes:
#   always           spotlessCheck — cheapest gate, most common red
#   code changed     ./gradlew build — tests, coverage thresholds, check
#   migration        ./scripts/check-migrations.sh, then the two-version drill
#                    — a migration the running version cannot survive fails
#                    here, against the released branch, not during a cutover
#   resolution       ./gradlew bootJar with an empty GRADLE_USER_HOME — proves
#                    the change resolves from nothing, which is what a runner
#                    does; a warm home hides missing checksums and metadata.
#                    Only the Gradle distribution is shared: fetching it again
#                    would test the network, not the change, and its failures
#                    were the ones that blocked a push for no reason.
#   build inputs     docker build — Dockerfile, context, Gradle wiring
#   --tree           run every step regardless of what the range touches
#   --master         also: the range must carry no AI-only paths
#
# Usage: scripts/preflight.sh [--tree] [--master]
# Prints PREFLIGHT: green | PREFLIGHT: RED; exits non-zero on red.
set -uo pipefail

cd "$(dirname "$0")/.."
root=$(git rev-parse --show-toplevel)
cd "$root"

# Where the warm build keeps its caches; the cold step borrows only its Gradle
# distribution from here.
warm_home="${GRADLE_USER_HOME:-$HOME/.gradle}"

all_tree=0
master=0
for arg in "$@"; do
  case "$arg" in
    --tree) all_tree=1 ;;
    --master) master=1 ;;
    -h | --help)
      sed -n '3,27p' "$0" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    *)
      echo "unknown argument: $arg" >&2
      exit 2
      ;;
  esac
done

if git rev-parse --verify --quiet origin/develop >/dev/null; then
  base=origin/develop
else
  base=origin/master
fi

changed=$(
  {
    git diff --name-only "$base"...HEAD
    git diff --name-only
    git diff --cached --name-only
    git ls-files --others --exclude-standard
  } | sort -u
)

matches() {
  printf '%s\n' "$changed" | grep -Eq "$1"
}

code_changed=0
resolve_changed=0
build_changed=0
migrations_changed=0
if [ "$all_tree" -eq 1 ]; then
  code_changed=1
  resolve_changed=1
  build_changed=1
  migrations_changed=1
else
  # Anything the compiler, the formatter or the test suite reads.
  matches '^(src/|gradle/|gradle\.properties$|build\.gradle\.kts$|settings\.gradle\.kts$)' && code_changed=1
  # Anything that decides which artifacts are fetched, from where, and whether
  # their bytes are the ones we signed off on.
  matches '^(gradle/|gradle\.properties$|build\.gradle\.kts$|settings\.gradle\.kts$)' && resolve_changed=1
  # Anything the image build copies or executes. `gradle-retry.sh` is in the
  # build stage, so a change to it changes the image.
  matches '^(src/|gradle/|gradle\.properties$|build\.gradle\.kts$|settings\.gradle\.kts$|Dockerfile$|\.dockerignore$|\.github/scripts/gradle-retry\.sh$)' &&
    build_changed=1
  # A migration is the change a running version cannot ignore: the static gate
  # compares it against the released branch even when only SQL moved. Editing
  # either gate script, the wiring that calls them, or the workflow is the same
  # risk with the sign flipped — the thing that answers the question changed —
  # so the drill runs for those too.
  matches '^(src/main/resources/db/migration/|scripts/check-migrations\.sh$|scripts/migration-coexistence-drill\.sh$|scripts/tests/check-migrations-test\.sh$|scripts/preflight\.sh$|\.github/workflows/ci\.yml$)' &&
    migrations_changed=1
fi

step=""
run() {
  step=$1
  shift
  echo ""
  echo "== $step"
  if ! "$@"; then
    echo ""
    echo "PREFLIGHT: RED ($step)" >&2
    exit 1
  fi
}

# A branch bound for master may carry code and user-facing documentation, and
# nothing else: the AI workspace and the develop-only plans must not ship.
if [ "$master" -eq 1 ]; then
  blocked=$(printf '%s\n' "$changed" | grep -E '^(memory-bank/|\.plans/|\.agents/|dev-docs/|docs/plans/|AGENTS\.md$)' || true)
  if [ -n "$blocked" ]; then
    echo "AI-only paths must not reach master:" >&2
    printf '%s\n' "$blocked" >&2
    echo ""
    echo "PREFLIGHT: RED (master range)" >&2
    exit 1
  fi
  echo "checked: the range carries no AI-only paths"
fi

run "format" ./gradlew -q spotlessCheck
if [ "$migrations_changed" -eq 1 ]; then
  run "migrations" ./scripts/check-migrations.sh
  run "migration gate test" ./scripts/tests/check-migrations-test.sh
  run "migration drill" ./scripts/migration-coexistence-drill.sh
else
  echo ""
  echo "== migrations: skipped, the range touches no migration"
fi

if [ "$code_changed" -eq 1 ]; then
  run "build and test" ./gradlew build
else
  echo ""
  echo "== build and test: skipped, no code in the range"
fi

cold_resolve() {
  local home status
  home=$(mktemp -d "${TMPDIR:-/tmp}/preflight-gradle.XXXXXX")
  # Every other part of the home stays empty, which is the whole point; the
  # distribution is the same bytes the warm build already runs, so reusing it
  # keeps a download out of a step that is about resolution.
  if [ -d "$warm_home/wrapper" ]; then
    ln -s "$warm_home/wrapper" "$home/wrapper"
  fi
  # Resolution fails on a 429 or a reset as readily as on a real defect; the
  # retry script separates the two, so the gate does not cry wolf.
  GRADLE_USER_HOME="$home" GRADLE_RETRY_LOG="$home/resolve.log" \
    .github/scripts/gradle-retry.sh bootJar -x test --no-daemon -q
  status=$?
  rm -rf "$home"
  return $status
}

if [ "$resolve_changed" -eq 1 ]; then
  run "cold resolve" cold_resolve
else
  echo ""
  echo "== cold resolve: skipped, no resolution wiring in the range"
fi

if [ "$build_changed" -eq 1 ]; then
  run "image build" docker build --quiet -t ctt-preflight:local .
  # The image is a means to an end; leaving it behind would be a leak of its own.
  docker image rm -f ctt-preflight:local >/dev/null 2>&1 || true
else
  echo ""
  echo "== image build: skipped, no build inputs in the range"
fi

echo ""
echo "PREFLIGHT: green"
