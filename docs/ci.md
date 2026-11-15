# Continuous Integration

`.github/workflows/ci.yml` runs on every pull request and on every push to
`develop` or `master`. It answers one question — may this tree be merged? — and
it answers with a red or green check rather than a judgement.

The Jenkins job in `Jenkinsfile` is **not** a gate, and it no longer builds. It
pulls the digest `publish.yml` signed, verifies the signature against that
workflow's identity, and health-checks the result on the deployment host.
Running the suite there as well would tie merge latency to a machine that also
runs production.

## Where the gates apply

`master` is protected by a repository ruleset: it cannot be deleted, force-pushed,
or written to directly — everything arrives through a pull request, and no one,
administrators included, is on a bypass list. `develop` is unprotected; the gates
still run on its pushes, they simply do not block anything there.

The three job names in this workflow are the checks the ruleset requires before a
merge, and they are in place. GitHub only accepts a required check that has
already completed successfully in the repository within the last seven days,
which is why they were written in this order: *open a pull request → let it go
green → write the checks into the ruleset → read the ruleset back*. A workflow
that is renamed without updating the ruleset leaves a pending check that blocks
every merge, so the two move together.

Branch naming, where each branch is cut from, and the tag rules live in one
place — [the branch model in `AGENTS.md`](../AGENTS.md) — so they cannot drift
between the two documents.

## What runs

| Job | Gate | Red when |
| --- | --- | --- |
| `quality` | `./gradlew spotlessCheck` | formatting drifts (a one-line fix, so it fails first) |
| `quality` | `./gradlew build jacocoTestReport jacocoTestCoverageVerification` | any test fails, or coverage drops below the thresholds in `build.gradle.kts` |
| `secrets` | `deploy/secrets/render.sh --env dev` with the CI age identity | the payload cannot be decrypted, a mounted secret is missing or not `0440`, or the rendered files are not ignored by git |
| `supply-chain` | `gitleaks detect` over the full history | a credential-shaped string is committed |
| `supply-chain` | `trivy fs` over the working tree | a `HIGH`/`CRITICAL` vulnerability or a committed secret appears in a scanned manifest |
| `supply-chain` | `docker buildx build --provenance=mode=max` | the Dockerfile does not build |
| `supply-chain` | provenance extraction and pairing | the built image carries no SLSA v1 attestation, or the attestation names a digest this layout does not contain |
| `supply-chain` | `trivy image` | the image ships a `HIGH`/`CRITICAL` vulnerability |
| `supply-chain` | CycloneDX SBOM | the image cannot be inventoried |

`quality` publishes the test and coverage reports even when it fails, and
`supply-chain` uploads all of its evidence (secret scan, filesystem scan,
provenance, metadata, SBOM, image scan) with `if: always()`, so a red build still
explains itself. Each scan writes its report before its exit code is decided;
the exit code never stands in for evidence.

## The image is built once

The scanned image and the attested image are the same build. Building twice
would let the digest that was scanned diverge from the digest that carries the
provenance, which is exactly the failure an attestation exists to rule out.

The build writes an OCI layout to `build/image.tar`; the attestation lives in it
as a second manifest whose annotation type is `attestation-manifest`.
`.github/scripts/extract-provenance.py` pulls that manifest out, checks that the
digest the attestation names is one of this layout's manifests, and writes the
SLSA predicate to `build/provenance.json`. It fails loudly when `--provenance`
was omitted, which is how the gate stays honest.

BuildKit writes an archive, while `trivy image --input` reads an unpacked layout:
pointing it at the tar fails with `unable to open … as an OCI Image: stat
…/index.json: not a directory`. The pipeline therefore unpacks `build/image.tar`
into `build/oci` and scans that, so the scan, the attestation and the SBOM all
describe one build.

## Publishing and signing

`ci.yml` builds an image so it can be scanned, then discards it. The deployable
image — the one with a tag and a signature — is built by `publish.yml`, on
`master` and on `v*` tags, pushed to `ghcr.io/ahogek/ctt-server`, and signed at
the digest it pushed.

Signing is keyless: GitHub issues a short-lived certificate to that run's OIDC
identity, cosign records the signature in the Rekor transparency log, and no
private key exists to store, rotate or leak. What that proves is **who signed**,
not what was built — a bare signature check would accept any workflow in any
repository. Two things close the gap:

- the certificate identity is pinned to `.github/workflows/publish.yml` on this
  repository, and the issuer to `https://token.actions.githubusercontent.com`;
- the provenance attestation, which `publish.yml` re-verifies, records the
  commit, the Dockerfile and the builder.

Verification takes a digest, never a tag: a tag can be moved between the check
and the container start, which swaps the bytes underneath a passing
verification.

```bash
IMAGE=ghcr.io/ahogek/ctt-server@sha256:<digest>
IDENTITY='https://github.com/AhogeK/ctt-server/.github/workflows/publish.yml@.*'

cosign verify \
  --certificate-identity-regexp "$IDENTITY" \
  --certificate-oidc-issuer https://token.actions.githubusercontent.com \
  "$IMAGE"

cosign verify-attestation --type slsaprovenance \
  --certificate-identity-regexp "$IDENTITY" \
  --certificate-oidc-issuer https://token.actions.githubusercontent.com \
  "$IMAGE"
```

The provenance reaches the registry twice, and only one of the two copies can be
verified. BuildKit keeps it inside the image index, addressed to the platform
manifest rather than to the index the release signs, and registries do not
expose it in a form `cosign` can query — the first publish run proved that
`cosign verify-attestation` answers `no matching attestations` for an image
that demonstrably carries one. So the pipeline takes the same provenance from
the build metadata and re-publishes it as a cosign attestation, which attaches
it to the digest that was signed. `publish.yml` then verifies it with the
exact command the deployment runs.

The published copy uses `mode=min`, because it lands in the public Rekor
transparency log and the maximal provenance carries the build environment.
The minimal one still records the commit the image was built from.

`deploy/verify-image.sh` applies both pins and rejects a tag before it contacts
the registry, so the check has one definition instead of one per caller. The
deploy job runs it and then `docker compose up -d --no-build`: a missing or
unverified image fails the deployment rather than falling back to a locally
built one nobody signed.

## The CI identity

`sops` needs one secret the repository cannot hold in plain text. The runner
receives it as the repository secret `SOPS_AGE_KEY`, writes it to
`$HOME/.config/sops/age/keys.txt` with mode `0600`, and calls `render.sh`
unchanged — the script has no CI-specific branch. The identity is the *dev*
payload's key: CI proves that rendering works, not that production can be
deployed.

The rendered files are `0440` and owned by the runner's group, exactly as on a
deployment host, because a mode regression would otherwise only show up when a
container refuses to start.

A pull request opened from a fork runs without repository secrets, so this job
skips itself there (`if:` on `head.repo.full_name`). Skipping is deliberate:
GitHub counts a skipped required check as passing, while a job that ran and
failed would leave outside contributions permanently unmergeable, with no way
for the contributor to fix it.

## Secret scanning and its allowlist

`gitleaks` scans the whole history, so a credential committed a year ago still
fails today. `.gitleaksignore` holds 41 fingerprints for findings that are
already in the history and are **not** live credentials: test fixtures,
documentation examples (OpenAPI example bodies included), and the plaintext
password block the Jenkins pipeline carried in earlier commits.

An entry is a fingerprint (`commit:path:rule:line`), not a path. A new finding
produces a different fingerprint — even inside one of those files, even on the
same line — and still fails the scan. Add an entry only after confirming the
value is fake, and never to silence a live credential.

## What the filesystem scan skips

`trivy fs` scans everything except `build/` and `.agents/`. The second is the
agent workspace: develop only, never on `master`, never shipped. Gating a merge
on the lockfile of a tool nobody deploys would be noise, and a silent skip is
worse than an explicit one, so the exclusion is spelled out in the workflow.

## Reproducing a gate locally

The scanners run as containers, so the exact versions from the workflow are the
ones you test against:

```bash
mkdir -p build

# formatting and the full suite with coverage
./gradlew spotlessCheck build jacocoTestReport jacocoTestCoverageVerification

# committed secrets (full history)
docker run --rm -v "$PWD:/repo" -w /repo ghcr.io/gitleaks/gitleaks:v8.30.1 \
  detect --source . --redact --verbose --no-banner \
  --report-format json --report-path /repo/build/gitleaks.json --exit-code 1

# filesystem vulnerabilities, secrets and misconfiguration
docker run --rm -v "$PWD:/repo" aquasec/trivy:0.76.2 \
  fs --scanners vuln,secret,misconfig --severity HIGH,CRITICAL \
  --skip-dirs /repo/.agents --skip-dirs /repo/build \
  --format json --output /repo/build/trivy-fs.json --exit-code 1 --no-progress /repo

# the image, its provenance and its SBOM
mkdir -p build
docker buildx build --provenance=mode=max \
  --output type=oci,dest=build/image.tar -t ctt-server:ci .
mkdir -p build/oci && tar -xf build/image.tar -C build/oci
python3 .github/scripts/extract-provenance.py build/oci build/provenance.json
docker run --rm -v "$PWD:/repo" aquasec/trivy:0.76.2 \
  image --input=/repo/build/oci --scanners vuln --severity HIGH,CRITICAL \
  --format json --output /repo/build/trivy-image.json --exit-code 1 --no-progress
docker run --rm -v "$PWD:/repo" aquasec/trivy:0.76.2 \
  image --input=/repo/build/oci --format cyclonedx \
  --output /repo/build/sbom.cdx.json --no-progress
```

Rendering secrets locally needs the age identity at
`$HOME/.config/sops/age/keys.txt`; without it, that job is the only one you
cannot reproduce on a laptop.

## When a gate is red

| Gate | First thing to check |
| --- | --- |
| formatting | run `./gradlew spotlessApply` and commit the result |
| tests / coverage | the failing class is in the uploaded `reports-<sha>` artifact; coverage names the package under the threshold |
| secrets | the fingerprint is new: remove the value and rotate it — the allowlist is not the fix |
| filesystem scan | the target path is in `build/trivy-fs.json`; upgrade the dependency or drop the manifest |
| provenance | the build ran without `--provenance=mode=max`, or the attestation names a digest missing from the layout |
| image scan | `build/trivy-image.json` names the package and the fixed version; bump the base image or the dependency |

## Related

- [Deployment and Secrets](deployment-secrets.md) — how the payloads are rendered outside CI.
- [Configuration](production-configuration.md) — the profile and environment contract the pipeline asserts.
- [Observability](observability.md) — what the deployed stack reports once this pipeline has shipped it.
