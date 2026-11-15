#!/usr/bin/env bash
#
# Refuses to hand a deployment an image this repository did not publish.
#
# The signature is only half of it: keyless signing binds a digest to a workflow
# identity, so "valid signature" alone proves nothing — any GitHub workflow in
# any repository can sign. What makes the check meaningful is that the identity
# and the issuer are pinned to *our* publish workflow on *our* repository, and
# that the reference is a digest so the bytes verified are the bytes started.
#
# Usage:
#   deploy/verify-image.sh ghcr.io/ahogek/ctt-server@sha256:<digest>
#
# Environment:
#   COSIGN_IDENTITY_REGEXP  override the workflow identity (forks)
#   COSIGN_ISSUER           override the OIDC issuer
#
# Exit codes: 0 verified, 1 verification failed, 2 usage or missing tooling.
set -euo pipefail

REPOSITORY="${REPOSITORY:-AhogeK/ctt-server}"
WORKFLOW="${WORKFLOW:-publish.yml}"
IDENTITY_REGEXP="${COSIGN_IDENTITY_REGEXP:-https://github.com/${REPOSITORY}/.github/workflows/${WORKFLOW}@.*}"
ISSUER="${COSIGN_ISSUER:-https://token.actions.githubusercontent.com}"

die() { echo "verify-image: $*" >&2; exit 2; }

[ $# -eq 1 ] || die "usage: $0 <image>@sha256:<digest>"
IMAGE_REF="$1"

case "$IMAGE_REF" in
  *@sha256:*) ;;
  *) die "reference must be digest-pinned (…@sha256:…), got ${IMAGE_REF};
     a tag can be moved after verification, a digest cannot" ;;
esac

# A registry path is lowercase by definition, so a reference carrying the
# repository's capital letters cannot be parsed by the registry client even
# though the digest behind it is correct.
name_part="${IMAGE_REF%@sha256:*}"
digest_part="${IMAGE_REF##*@sha256:}"
IMAGE_REF="$(printf '%s' "$name_part" | tr '[:upper:]' '[:lower:]')@sha256:${digest_part}"

command -v cosign >/dev/null 2>&1 || die "cosign is not installed (releases: https://github.com/sigstore/cosign/releases)"

echo "verify-image: pinning identity to ${IDENTITY_REGEXP}"
echo "verify-image: pinning issuer to ${ISSUER}"

# The certificate the workflow was issued proves who signed; the transparency
# log entry proves when. cosign checks both and fails on an expired certificate
# that was not valid at signing time.
cosign verify \
  --certificate-identity-regexp "$IDENTITY_REGEXP" \
  --certificate-oidc-issuer "$ISSUER" \
  "$IMAGE_REF" >/dev/null

# The signature says "this digest exists"; the attestation says how it was
# built. A deployment that only checks the signature would accept an image the
# workflow signed but nobody built from our commit.
cosign verify-attestation \
  --type slsaprovenance \
  --certificate-identity-regexp "$IDENTITY_REGEXP" \
  --certificate-oidc-issuer "$ISSUER" \
  "$IMAGE_REF" >/dev/null

echo "verify-image: verified ${IMAGE_REF}"
