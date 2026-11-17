#!/usr/bin/env bash
#
# Runs the previous release and the working tree against one schema at the same
# time, and refuses to call it green unless both keep serving.
#
# Why a real second version: the static gate (scripts/check-migrations.sh) can
# see that a migration drops a column, but not that the previous version reads
# it. During a blue-green cutover the migration runs while the old colour still
# answers requests, so a destructive statement is only safe after the old
# version is gone. This drill makes that window reproducible: the released
# revision applies the released migrations, serves that schema, and is
# exercised again after the new version has applied its own.
#
# The old jar is built from a revision rather than pulled from a registry: the
# candidate migration is not published yet, so no published image can hold the
# pair this drill compares, and the released branch is the only thing that says
# which code the old colour is running. `latest` is published on release tags,
# but a tag still cannot contain a migration that has not been released.
#
# Everything runs in throwaway containers and a throwaway worktree; nothing
# touches the local database or the checkout's build output beyond `bootJar`.
#
# Usage: scripts/migration-coexistence-drill.sh [--old-ref <revision>] [--keep]
#   --old-ref   released revision whose jar represents the running version
#               (default: origin/master, then master)
#   --keep      keep the jars and logs; containers and worktree go either way
# Prints DRILL: green | DRILL: RED (...); exits non-zero on red.
set -uo pipefail
# A hook runs this with GIT_INDEX_FILE pointing at the caller's index; the
# worktree below has a .git file instead of a directory, so inheriting it
# fails at checkout with "index file open failed".
unset GIT_DIR GIT_WORK_TREE GIT_INDEX_FILE GIT_OBJECT_DIRECTORY GIT_COMMON_DIR

cd "$(dirname "$0")/.."
root=$(git rev-parse --show-toplevel)
cd "$root"

old_ref=""
keep=0
while [ $# -gt 0 ]; do
  case "$1" in
    --old-ref)
      old_ref=${2:?--old-ref needs a revision}
      shift 2
      ;;
    --keep)
      keep=1
      shift
      ;;
    -h | --help)
      sed -n '3,27p' "$0" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    *)
      printf 'unknown argument: %s\n' "$1" >&2
      exit 2
      ;;
  esac
done

if [ -z "$old_ref" ]; then
  for candidate in origin/master master; do
    if git rev-parse --verify --quiet "$candidate" >/dev/null 2>&1; then
      old_ref=$candidate
      break
    fi
  done
fi
if [ -z "$old_ref" ]; then
  printf 'DRILL: RED (no released revision; pass --old-ref)\n'
  exit 1
fi

for tool in docker git java curl jq; do
  if ! command -v "$tool" >/dev/null 2>&1; then
    printf 'DRILL: RED (missing %s)\n' "$tool"
    exit 1
  fi
done
if ! docker info >/dev/null 2>&1; then
  printf 'DRILL: RED (docker is not running)\n'
  exit 1
fi

tmp=$(mktemp -d "${TMPDIR:-/tmp}/migration-drill.XXXXXX")
db=ctt-drill-db
redis=ctt-drill-redis
mail=ctt-drill-mail
old_pid=""
new_pid=""
started_pid=""
worktree=""

cleanup() {
  [ -n "$old_pid" ] && kill "$old_pid" 2>/dev/null
  [ -n "$new_pid" ] && kill "$new_pid" 2>/dev/null
  wait 2>/dev/null
  [ -n "$worktree" ] && git worktree remove --force "$worktree" >/dev/null 2>&1
  docker rm -f "$db" "$redis" "$mail" >/dev/null 2>&1
  if [ "$keep" -eq 1 ]; then
    printf 'logs and jars kept in %s\n' "$tmp"
  else
    rm -rf "$tmp"
  fi
}
trap cleanup EXIT

fail() {
  printf 'DRILL: RED (%s)\n' "$1"
  if [ -n "${2:-}" ] && [ -f "$2" ]; then
    printf -- '--- %s (last 40 lines) ---\n' "$2"
    tail -40 "$2"
  fi
  exit 1
}

free_port() {
  local port=$1
  while lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1 || nc -z 127.0.0.1 "$port" >/dev/null 2>&1; do
    port=$((port + 1))
  done
  printf '%s' "$port"
}

publish_port() {
  docker port "$1" "$2" 2>/dev/null | head -1 | sed 's/.*://'
}

wait_ready() {
  local port=$1 name=$2 log=$3 pid=$4
  local deadline=$((SECONDS + 180))
  while [ "$SECONDS" -lt "$deadline" ]; do
    if ! kill -0 "$pid" 2>/dev/null; then
      fail "$name exited during startup" "$log"
    fi
    if [ "$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$port/actuator/health/readiness" 2>/dev/null)" = 200 ]; then
      return 0
    fi
    sleep 1
  done
  fail "$name did not become ready within 180 s" "$log"
}

# request <expected-code> <label> <curl args...>
request() {
  local expected=$1 label=$2
  shift 2
  local code
  code=$(curl -s -S -o "$tmp/body.json" -w '%{http_code}' "$@" 2>"$tmp/curl.err")
  if [ "$code" != "$expected" ]; then
    printf '  %s answered %s, expected %s\n' "$label" "$code" "$expected"
    head -20 "$tmp/body.json"
    [ -s "$tmp/curl.err" ] && head -5 "$tmp/curl.err"
    return 1
  fi
  return 0
}

# application.yaml imports `optional:file:.env[.properties]`, and the checkout's
# .env carries real hCaptcha keys — started from the repository root, the drill
# would demand a captcha token the script cannot produce. The jar is
# self-contained, so it runs from the scratch directory instead; that means .env
# no longer supplies the keys, so the two encryption keys are passed explicitly.
# An unresolved ${OAUTH_TOKEN_ENCRYPTION_KEY} keeps its literal text and dies in
# AesGcmTokenEncryptor with "Illegal base64 character 24" (hex for '$').
drill_key=$(printf 'A%.0s' $(seq 32) | base64 | tr -d '\n')

start_app() {
  local jar=$1 port=$2 mgmt=$3 log=$4
  (
    cd "$tmp" || exit 1
    exec env SERVER_PORT="$port" MANAGEMENT_PORT="$mgmt" \
      SPRING_PROFILES_ACTIVE=dev \
      DB_HOST=127.0.0.1 DB_PORT="$db_port" DB_NAME=ctt_drill \
      DB_USERNAME=postgres DB_PASSWORD=drill \
      REDIS_HOST=127.0.0.1 REDIS_PORT="$redis_port" REDIS_PASSWORD=drill \
      MAIL_SMTP_HOST=127.0.0.1 MAIL_SMTP_PORT="$mail_port" \
      MAIL_SMTP_USERNAME=drill MAIL_SMTP_PASSWORD=drill \
      HCAPTCHA_SITE_KEY= HCAPTCHA_SECRET_KEY= \
      JWT_SECRET_KEY="$jwt_secret" \
      OAUTH_TOKEN_ENCRYPTION_KEY="$drill_key" TOTP_ENCRYPTION_KEY="$drill_key" \
      java -jar "$jar" >"$log" 2>&1
  ) &
  started_pid=$!
}

# The build directory keeps every jar this checkout has ever produced, so the
# name has to be the exact version rather than whatever `find` returns first.
jar_for() {
  local dir=$1 version
  version=$(sed -n 's/^appVersion = "\(.*\)"$/\1/p' "$dir/gradle/libs.versions.toml")
  [ -n "$version" ] || return 1
  [ -f "$dir/build/libs/ctt-server-$version.jar" ] &&
    printf '%s' "$dir/build/libs/ctt-server-$version.jar"
}

jwt_secret=$(printf 'drill-%s' "$(date +%s)-0123456789abcdef0123456789abcdef")

# --- build both versions -----------------------------------------------------
printf '== build %s\n' "$old_ref"
worktree="$tmp/old"
git worktree add -q --detach "$worktree" "$old_ref" || fail "cannot check out $old_ref"
(cd "$worktree" && ./gradlew bootJar -x test -q --console=plain) || fail "building $old_ref"
old_jar=$(jar_for "$worktree")
[ -n "$old_jar" ] || fail "no jar built for $old_ref"

printf '== build working tree\n'
./gradlew bootJar -x test -q --console=plain || fail "building the working tree"
new_jar=$(jar_for "$root")
[ -n "$new_jar" ] || fail "no jar built for the working tree"
printf '   old %s\n   new %s\n' "$(basename "$old_jar")" "$(basename "$new_jar")"

# --- infrastructure ----------------------------------------------------------
printf '== containers\n'
# CI exports TESTCONTAINERS_HUB_IMAGE_NAME_PREFIX so that anonymous Docker Hub
# pulls are not what turns a required check red; plain `docker run` ignores it.
hub=${TESTCONTAINERS_HUB_IMAGE_NAME_PREFIX:-}
[ -z "$hub" ] || hub=${hub%/}/
docker rm -f "$db" "$redis" "$mail" >/dev/null 2>&1
docker run -d --rm --name "$db" -e POSTGRES_PASSWORD=drill -e POSTGRES_DB=ctt_drill \
  -p 127.0.0.1::5432 "${hub}library/postgres:latest" >/dev/null || fail "cannot start postgres"
docker run -d --rm --name "$redis" -p 127.0.0.1::6379 "${hub}library/redis:latest" \
  redis-server --requirepass drill >/dev/null || fail "cannot start redis"
docker run -d --rm --name "$mail" -p 127.0.0.1::1025 "${hub}axllent/mailpit:latest" >/dev/null ||
  fail "cannot start mailpit"
db_port=$(publish_port "$db" 5432/tcp)
redis_port=$(publish_port "$redis" 6379/tcp)
mail_port=$(publish_port "$mail" 1025/tcp)
for _ in $(seq 1 60); do
  docker exec "$db" pg_isready -U postgres -d ctt_drill >/dev/null 2>&1 && break
  sleep 1
done
docker exec "$db" pg_isready -U postgres -d ctt_drill >/dev/null 2>&1 || fail "postgres never accepted connections"
printf '   postgres %s redis %s smtp %s\n' "$db_port" "$redis_port" "$mail_port"

# --- old version: migrate, register, serve -----------------------------------
old_port=$(free_port 18191)
old_mgmt=$(free_port 18291)
new_port=$(free_port 18194)
new_mgmt=$(free_port 18294)

printf '== start %s\n' "$(basename "$old_jar")"
start_app "$old_jar" "$old_port" "$old_mgmt" "$tmp/old.log"
old_pid=$started_pid
wait_ready "$old_mgmt" "old version" "$tmp/old.log" "$old_pid"

email="drill-$(date +%s)@example.test"
terms=$(curl -sf "http://127.0.0.1:$old_port/ctt-server/api/v1/config/public" | jq -r '.data.termsVersion // empty')
[ -n "$terms" ] || fail "old version does not serve /api/v1/config/public" "$tmp/old.log"
printf '== register a user on the old version (terms %s)\n' "$terms"
request 200 "register" -X POST "http://127.0.0.1:$old_port/ctt-server/api/v1/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$email\",\"displayName\":\"drill\",\"password\":\"DrillPass123!\",\"termsVersion\":\"$terms\"}" ||
  fail "registration on the old version" "$tmp/body.json"

docker exec "$db" psql -U postgres -d ctt_drill -q -c \
  "UPDATE users SET status = 'ACTIVE', email_verified = true WHERE email = '$email'" >/dev/null ||
  fail "cannot activate the drill user"

login() {
  local port=$1 label=$2
  request 200 "$label" -X POST "http://127.0.0.1:$port/ctt-server/api/v1/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$email\",\"password\":\"DrillPass123!\",\"deviceId\":\"drill-device\"}" || return 1
  jq -r '.data.accessToken // empty' "$tmp/body.json"
}

old_token=$(login "$old_port" "login on the old version") || fail "login on the old version" "$tmp/body.json"
[ -n "$old_token" ] || fail "old version returned no access token" "$tmp/body.json"

request 200 "old version: /users/me" "http://127.0.0.1:$old_port/ctt-server/api/v1/users/me" \
  -H "Authorization: Bearer $old_token" || fail "old version cannot read its own user" "$tmp/body.json"

migrations_before=$(docker exec "$db" psql -U postgres -d ctt_drill -tAc \
  "SELECT count(*) FROM flyway_schema_history WHERE success")
printf '== old version applied %s migration(s)\n' "$migrations_before"

# --- new version: migrate on top, then both must serve ----------------------
printf '== start %s on the same database\n' "$(basename "$new_jar")"
start_app "$new_jar" "$new_port" "$new_mgmt" "$tmp/new.log"
new_pid=$started_pid
wait_ready "$new_mgmt" "new version" "$tmp/new.log" "$new_pid"

migrations_after=$(docker exec "$db" psql -U postgres -d ctt_drill -tAc \
  "SELECT count(*) FROM flyway_schema_history WHERE success")
failed_migrations=$(docker exec "$db" psql -U postgres -d ctt_drill -tAc \
  "SELECT count(*) FROM flyway_schema_history WHERE NOT success")
[ "$failed_migrations" = 0 ] || fail "flyway_schema_history records a failed migration" "$tmp/new.log"
[ "$migrations_after" -ge "$migrations_before" ] ||
  fail "the new version applied fewer migrations ($migrations_after) than the old ($migrations_before)" "$tmp/new.log"
printf '== new version applied %s → %s migration(s)\n' "$migrations_before" "$migrations_after"

new_token=$(login "$new_port" "login on the new version") || fail "login on the new version" "$tmp/body.json"
request 200 "new version: /users/me" "http://127.0.0.1:$new_port/ctt-server/api/v1/users/me" \
  -H "Authorization: Bearer $new_token" || fail "new version cannot read the user the old version created" "$tmp/body.json"
request 200 "new version: /users/me/sessions" "http://127.0.0.1:$new_port/ctt-server/api/v1/users/me/sessions" \
  -H "Authorization: Bearer $new_token" || fail "new version cannot list sessions" "$tmp/body.json"

# The point of the drill: the old version was not stopped while the new one
# migrated, and it must still be able to serve with its own token.
wait_ready "$old_mgmt" "old version after the migration" "$tmp/old.log" "$old_pid"
request 200 "old version after migration: /users/me" "http://127.0.0.1:$old_port/ctt-server/api/v1/users/me" \
  -H "Authorization: Bearer $old_token" || fail "the old version broke on the migrated schema" "$tmp/old.log"
request 200 "old version after migration: /users/me/sessions" "http://127.0.0.1:$old_port/ctt-server/api/v1/users/me/sessions" \
  -H "Authorization: Bearer $old_token" || fail "the old version cannot list sessions on the migrated schema" "$tmp/old.log"
request 200 "old version after migration: login" -X POST "http://127.0.0.1:$old_port/ctt-server/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$email\",\"password\":\"DrillPass123!\",\"deviceId\":\"drill-device\"}" || fail "the old version can no longer log the user in" "$tmp/old.log"

printf 'DRILL: green (%s and the working tree both served on %s migration(s))\n' \
  "$(basename "$old_jar")" "$migrations_after"
