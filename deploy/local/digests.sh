#!/usr/bin/env bash
# Record the digest of every image the local stack runs (P0.2).
#
#   ./deploy/local/digests.sh                # print a Markdown table
#   ./deploy/local/digests.sh --out FILE     # append the table to FILE
#
# Every image tracks `latest`, so a tag cannot identify what ran. The digest
# can. Record before and after an upgrade rehearsal and compare the two blocks.
set -euo pipefail

cd "$(dirname "$0")/../.."

out=""
if [ "${1:-}" = "--out" ] && [ -n "${2:-}" ]; then
  out="$2"
fi

images="$(grep -hE '^[[:space:]]+image:' docker-compose.yaml docker-compose.local.yaml \
  | awk '{print $2}' | sort -u | grep -v '^ctt-server-app$')"

rows=""
missing=0
while IFS= read -r image; do
  digest="$(docker image inspect --format '{{if .RepoDigests}}{{index .RepoDigests 0}}{{else}}built-locally{{end}}' "$image" 2>/dev/null || true)"
  if [ -z "$digest" ]; then
    rows="${rows}| \`$image\` | missing, run \`docker compose pull\` |"$'\n'
    missing=1
  else
    rows="${rows}| \`$image\` | \`$digest\` |"$'\n'
  fi
done <<< "$images"

app="$(docker image inspect --format '{{.Id}}' ctt-server-app:latest 2>/dev/null || true)"
if [ -n "$app" ]; then
  rows="${rows}| \`ctt-server-app\` (built on the host) | \`${app:0:19}\` |"$'\n'
fi

section="## $(date -u '+%Y-%m-%dT%H:%M:%SZ')

| Image | Digest |
|---|---|
${rows}"

if [ -n "$out" ]; then
  mkdir -p "$(dirname "$out")"
  printf '%s\n' "$section" >> "$out"
  echo "recorded in $out"
else
  printf '%s' "$section"
fi

exit "$missing"
