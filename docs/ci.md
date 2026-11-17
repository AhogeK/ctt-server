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

The gate job names in this workflow are the checks the ruleset requires before a
merge; deleting or renaming one leaves a pending check that blocks every merge.
GitHub only accepts a required check that has already completed successfully, so
a new job is added in this order: *open a pull request → let it go green → write
it into the ruleset → read the ruleset back*. Until `Migration compatibility` is
in that list it reports on the pull request but does not block the merge.

Branch naming, where each branch is cut from, and the tag rules live in one
place — [the branch model in `AGENTS.md`](../AGENTS.md) — so they cannot drift
between the two documents.

## What runs

| Job | Gate | Red when |
| --- | --- | --- |
| `quality` | `./gradlew spotlessCheck` | formatting drifts (a one-line fix, so it fails first) |
| `quality` | `./gradlew build jacocoTestReport jacocoTestCoverageVerification` | any test fails, or coverage drops below the thresholds in `build.gradle.kts` |
| `migrations` | `scripts/check-migrations.sh` | a migration renames or drops something the running version still reads, edits a migration `master` already applied, or a file name breaks the `V<timestamp>__<name>.sql` shape |
| `migrations` | `scripts/tests/check-migrations-test.sh` | the static gate stops catching a shape it claims to: the regression test and the gate disagree |
| `migrations` | `scripts/migration-coexistence-drill.sh` (when the classifier flags a schema or gate change) | the previous release and the new build cannot both serve one database after the new migrations run |
| `secrets` | `deploy/secrets/render.sh --env dev` with the CI age identity | the payload cannot be decrypted, a mounted secret is missing or not `0440`, or the rendered files are not ignored by git |
| `supply-chain` | `gitleaks detect` | a credential-shaped string is committed — on a pull request only the commits under review are scanned, on `develop`/`master` the whole history |
| `supply-chain` | `trivy fs` over the working tree | a `HIGH`/`CRITICAL` vulnerability or a committed secret appears in a scanned manifest |
| `supply-chain` | `docker buildx build --provenance=mode=max` with a layer cache | the Dockerfile does not build |
| `supply-chain` | provenance extraction and pairing | the built image carries no SLSA v1 attestation, or the attestation names a digest this layout does not contain |
| `supply-chain` | `trivy image` | the image ships a `HIGH`/`CRITICAL` vulnerability |
| `supply-chain` | CycloneDX SBOM | the image cannot be inventoried |

`migrations` answers a question the other gates cannot: *can the release that is
running right now survive the schema change?* A blue-green cutover runs both
colours against one database, so a migration that drops, renames or constrains
something the old colour reads turns every request from the draining side into a
500 — and the damage is already committed by the time the new colour is healthy.
Two steps enforce it, both runnable locally (`scripts/check-migrations.sh` and
`scripts/migration-coexistence-drill.sh`):

- the static check rejects editing a migration `master` already applied (Flyway
  would refuse the file anyway, one deploy later and with the stack down), a
  file name that breaks the `V<timestamp>__<name>.sql` shape, and the
  breaking patterns listed in
  `## Schema changes during a blue-green cutover` in `docs/ops-runbook.md`
  unless the file carries a `-- contract:` note naming when the old shape
  disappears;
- the drill builds the previous release and the working tree, starts both
  against one scratch database, registers and logs in on the old build, applies
  the new migrations, then asks *both* to serve — a change that breaks the
  running colour fails here, before it reaches a deploy.

The static check runs on every code change (seconds). The drill follows the
classifier: it runs when the range adds or edits a file under
`src/main/resources/db/migration` — the only way to break the running colour —
and also when it edits either gate script, the gate's regression test,
`scripts/preflight.sh`, or this workflow, because then the answer changed rather
than the schema. That scope
lives in two places, the classifier and `scripts/preflight.sh`, and both have
to change together.

The drill needs Docker and a warm Gradle cache: about twenty seconds warm, a
couple of minutes when the previous release has to be built from scratch. It is
the reason the gate is worth having rather than a syntax check that a
`DROP COLUMN` passes.

`quality` publishes the test and coverage reports even when it fails, and
`supply-chain` uploads all of its evidence (secret scan, filesystem scan,
provenance, metadata, SBOM, image scan) with `if: always()`, so a red build still
explains itself. Each scan writes its report before its exit code is decided;
the exit code never stands in for evidence.

## Documentation-only pull requests

A pull request that touches nothing but prose does not run the suite. A
`changes` job classifies the diff first and the expensive jobs read its answer —
`quality`, `migrations` and `supply-chain`. On a documentation-only pull request
they check out nothing, run no Gradle task, scan nothing and finish in seconds.
Measured on run `43792194309`, the suite job took 6m13s of wall time, which is
the wait this removes.

The allowlist is deliberately narrow — a path counts as documentation only when
every changed file matches `*.md`, `docs/`, `.plans/` or `memory-bank/`.
Everything else is code for this purpose: a workflow edit, a compose change or a
dependency bump looks harmless in a diff and none of them are, so a generous
classifier costs a few minutes while a permissive one would ship untested
changes.

Two details keep this from becoming a way to merge without being tested:

- **It fails closed.** The gate jobs always run (`if: always()`), and a step
  that fails the job runs whenever the classifier did not finish. A skipped
  required check counts as passing on GitHub, so a gate that could be skipped by
  breaking the classifier would be worse than no gate.
- **The `secrets` job is not gated.** It renders the payloads and checks that
  the output is git-ignored, which costs a few seconds and would catch a
  documentation pull request that also committed a rendered secret.

Pushes to `develop` and `master` never take the short path: the classifier
answers `code=true` for every event that is not a pull request.

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

### What the registry holds

One publish run pushes several tags, and only one of them is a release:

| Tag | Points at | Moves |
| --- | --- | --- |
| `sha-<short>` | the newest build of that commit | yes — a second build of the same commit is a different image |
| `master` | the newest branch build | yes, with every push to `master` |
| `X.Y.Z` | the release | no |
| `latest` | the newest release | only when a release is published |

The `vX.Y.Z` git tag publishes the image tag `X.Y.Z`: the `v` belongs to the
git tag, the image tag has none. `latest` follows releases rather than the
branch — the publish workflow turns the action's own `latest` off and adds the
tag itself for a release only, so an unreleased branch build never becomes the
image somebody pulls by name.

None of these tags are a deployment handle. The build is not byte-reproducible,
so two builds of one commit are two different images and the tag that names
them points at whichever landed last; a digest names one image and does not
move. Deployments pin a digest, and `deploy/verify-image.sh` refuses a tag.

To see what the registry currently holds, list the tags with their digests. The
registry answers a manifest request with the digest in a header, so a pull
token is enough — no image has to be downloaded:

```bash
REPO=ahogek/ctt-server
TOKEN=$(curl -fsS "https://ghcr.io/token?scope=repository:$REPO:pull&service=ghcr.io" | jq -r .token)
for tag in $(curl -fsS -H "Authorization: Bearer $TOKEN" "https://ghcr.io/v2/$REPO/tags/list" | jq -r '.tags | sort | .[]'); do
  digest=$(curl -fsSI -H "Authorization: Bearer $TOKEN" \
    -H 'Accept: application/vnd.oci.image.index.v1+json' \
    "https://ghcr.io/v2/$REPO/manifests/$tag" | tr -d '\r' | awk -F': ' 'tolower($1)=="docker-content-digest"{print $2}')
  printf '%-15s %s\n' "$tag" "$digest"
done
```

A `401` from these endpoints means the caller has no `read:packages` scope; it
does not mean the package is private. `docker pull` by digest is the test for
that, and it works without credentials.

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

## Where artifacts come from

Maven Central throttles shared addresses with `429 Too Many Requests`, and a
GitHub-hosted runner is one of the most shared addresses there is. Resolution
therefore goes through a byte-identical mirror declared first in
`settings.gradle.kts` — `artifactMirrorUrl` in `gradle.properties`, Google's
unauthenticated copy of Central — with Central kept behind it for artifacts
newer than the mirror's replication. `FAIL_ON_PROJECT_REPOS` keeps a project
from quietly adding a source of its own.

The mirror does not have to be trusted: `gradle/verification-metadata.xml` pins
the sha256 of every artifact, so a mirror serving different bytes fails the
build.

Plugins resolve through the same mirror, and leaving them out is a known way to
ship a broken `master`: the Plugin Portal *redirects* `plugins.gradle.org/m2/…`
to `repo.maven.apache.org`, so the plugin classpath still came from the
throttled host. Only a cold build noticed — `Publish` on a fresh `master` died
on `429` while the `CI` run for the same commit was green because its dependency
layer was a cache hit. `pluginManagement` therefore declares the mirror first
and the portal second, for plugins the mirror does not carry.

The portal does rewrite a marker's POM: `org.springframework.boot.gradle.plugin`
is `0f7de85e…` from the portal and `221abccf…` from Central. The verification
metadata lists **both** hashes for the four markers, which is Gradle's
documented way to accept either source — a copy from anywhere else still fails.

A version *range* is a third way the throttling reaches the build. Gradle answers
`[4.5.1,5)` by listing versions in **every** repository, and a repository that
fails is disabled for the rest of the build — so one `429` on a repository that
holds nothing the answer needs still fails the resolve, because Gradle cannot
know which version is highest. `com.yubico:webauthn-server-parent` ships two such
ranges (cbor, guava); `build.gradle.kts` pins both with `strictly`, which
replaces the request instead of consulting listings. The proof that the listing
is gone is a cold resolve under `--info`: no `maven-metadata.xml` request appears.

Two layers keep the download from recurring:

- `downloadDependencies` resolves every configuration without compiling, and the
  image build runs it in a layer that only depends on the build scripts and the
  version catalogue. An unchanged dependency set is a cache hit, and the image
  build never reaches a repository.
- The workflow exports the build cache with `mode=max`. `mode=min` exports only
  layers of the final image, and the builder stage is not one of them — with
  `min`, every run downloaded the whole graph again.

The dependency layer is a plain layer, not a `--mount=type=cache`: Docker does
not export cache mounts to `--cache-to`, and a fresh runner starts them empty, so
a mount over `/root/.gradle` would download the graph again on every run and
shadow the copy the layer already holds. The cost of that choice is cache size —
a dependency change adds the Gradle home (~410 MB) to the image cache, where it
stays until evicted.

Retrying is the last line, not the first: `.github/scripts/gradle-retry.sh`
repeats a command once when the failure names a registry or container runtime,
and never when it names a test, a coverage gap or a formatting violation.

Container images fail the same way: Docker Hub counts anonymous pulls per
source address, and one runner hosts many builds. `postgres:latest` and
`redis:latest` are therefore pulled through Google's hub mirror —
`TESTCONTAINERS_HUB_IMAGE_NAME_PREFIX` in the workflow covers every image
Testcontainers pulls, its Ryuk sidecar included — and the builder carries the
same mirror as its first choice for base images, falling back to Docker Hub for
anything the mirror does not carry.

## Before the change leaves the machine

`scripts/preflight.sh` runs what CI runs, scoped to what changed: formatting
always, the full suite when code changed, a cold resolve when the resolution
wiring changed, the image when a build input changed, the migration gates when
the schema or one of those gates changed, and — with `--master` — a refusal to
carry AI-only paths into a branch bound for master. It prints one
verdict, `PREFLIGHT: green` or `PREFLIGHT: RED`, and the repository installs it
as the push gate:

The cold resolve exists because a warm `GRADLE_USER_HOME` answers from cached
metadata: it cannot show a missing checksum entry or an unreachable source, and
both shipped a red pipeline while every local command was green.
The empty home covers dependency metadata, not the Gradle distribution: the step
links the warm home's `wrapper/dists` into the temporary one, because re-fetching
the same bytes tests the network rather than the change, and a transient failure
there once blocked a push whose content was already green. Resolution failures
are read by `gradle-retry.sh`, so a `429` or a reset is retried once and a real
defect still fails on the first attempt.


```bash
git config core.hooksPath .githooks   # pre-commit: preflight for code, formatting for docs; pre-push: preflight
```

A green pipeline is a claim, and the person who pushed owns it:
`.github/scripts/wait-for-head.sh` blocks until **every** run for the pushed
commit reports a verdict — on `master` that means `CI` *and* `Publish`, not the
first one to finish. Resolving by SHA matters (for the first seconds after a
push, `gh run list --limit 1` still answers with the previous run), and waiting
for all of them matters more: a green `CI` beside a red `Publish` is not a
delivery. Reading the list itself is retried: `gh` has transport failures of
its own (one wait died on a bare `EOF` after 88 minutes), so a lost reply is
retried three times instead of ending the wait, and a list that cannot be read
at all prints `VERDICT: RED (cannot list runs)` rather than exiting quietly.

## Reproducing a gate locally

The scanners run as containers, so the exact versions from the workflow are the
ones you test against:

```bash
mkdir -p build

# formatting and the full suite with coverage
./gradlew spotlessCheck build jacocoTestReport jacocoTestCoverageVerification

# committed secrets. On a pull request CI scans only the commits under review;
# `--log-opts=origin/develop..HEAD` reproduces that locally, and dropping the
# flag is what the branch builds do.
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

`sops` and `cosign` are not baked into any gate's image — install the pinned pair with
`./scripts/install.sh --tool all`. The script verifies the project's own checksums, stages each
binary beside its target before replacing it, and prints the `export PATH` line when the prefix is
outside `PATH`.

### What `check` enforces without a runner

`./gradlew check` is the same judgement the `Format, tests and coverage` job makes: formatting,
the suite, and the coverage thresholds (instruction 80%, branch 70%). The coverage verification
is a dependency of `check` — before that it was declared with thresholds and nothing depended on
it, so only the workflow's explicit task name ever ran it.

Two things make a local run match the runner:

- **Dependency checksums.** `gradle/verification-metadata.xml` pins the sha256 of every resolved
  artifact, so a swap in a repository fails the build rather than entering it.
  `--write-verification-metadata` regenerates it after a deliberate dependency change. Prove the
  recorded set is complete with an empty Gradle home — `GRADLE_USER_HOME=$(mktemp -d) ./gradlew
  bootJar -x test` — because a warm cache serves files it never verified and hides the gap until a
  cold runner downloads them.
- **Timeouts.** The `test` task stops after 20 minutes and JUnit fails any single test after 5, so a
  hung test reports itself instead of consuming the runner's whole budget.

Dependency locking is deliberately absent: versions are centralized in `gradle/libs.versions.toml`
with no dynamic versions, so a lockfile would only add churn to every bump while the checksums
already cover "the package changed". `test` also does not fork in parallel — the suite starts real
PostgreSQL and Redis containers, and parallel forks would multiply them.

## When a gate is red

| Gate | First thing to check |
| --- | --- |
| formatting | run `./gradlew spotlessApply` and commit the result |
| tests / coverage | the failing class is in the uploaded `reports-<sha>` artifact; coverage names the package under the threshold |
| secrets | the fingerprint is new: remove the value and rotate it — the allowlist is not the fix |
| filesystem scan | the target path is in `build/trivy-fs.json`; upgrade the dependency or drop the manifest |
| provenance | the build ran without `--provenance=mode=max`, or the attestation names a digest missing from the layout |
| image scan | `build/trivy-image.json` names the package and the fixed version; bump the base image or the dependency |

A red check that the commit cannot explain is usually the runner, not the tree.
Two failures have actually happened: `Received status code 429 from server: Too
Many Requests` while resolving the Spring Boot Gradle plugin (Maven Central
rate-limits the shared runner addresses), and `Could not connect to Ryuk` when
Testcontainers reached Docker before it was ready. Both are retried once by
`.github/scripts/gradle-retry.sh`, which matches only those external signatures
— a failing test, a coverage gap or a formatting violation is not retried,
because a rerun would report the same failure and a green tick would hide it.

The image build runs the same wrapper, not its own copy: `Dockerfile` copies
`.github/scripts/gradle-retry.sh` and calls it for `bootJar`, because the build
stage downloads the same dependencies and a `429` there turned a merge red on
the `push` run while the pull request had passed. `.dockerignore` excludes
`.github/*` and re-includes exactly that one file, so the classifier stays a
single source; a change to the signatures applies to both paths.

Re-running a job is therefore the right response to a 429 and the wrong
response to a red test. Before concluding that a merge is fine, check the run
the merge itself started: a pull request's green checks do not cover the `push`
run on the base branch, which is a different run on a different commit.

| Symptom | Read as |
| --- | --- |
| `status code 429`, then the retry passes | registry throttling; nothing to fix |
| `status code 429` on both attempts | wait a few minutes and re-run the job |
| the `push` run is red while the pull request was green | the merge commit is untested — look at this run, not the pull request |

## When a gate fails, someone hears it

`.github/workflows/ci-notify.yml` turns a red run into a Telegram message. It
listens for `CI` and `Publish` finishing through `workflow_run` and sends only
when the conclusion is `failure`; a recovery message would repeat what the next
green run already says, on a channel one person reads.

The message carries the repository, workflow, branch, short commit, run attempt,
the names of the jobs that failed, and a link. The credentials are repository
secrets — `TELEGRAM_BOT_TOKEN` and `TELEGRAM_CHAT_ID`, whose values live in the
SOPS payload, never in the workflow. When the secrets are absent (any fork) the
step prints a line and exits zero: a fork has no operator to page, and failing
there would red every fork run forever.

Two limits are worth knowing. `workflow_run` only fires for a workflow that
already exists on the default branch, so a change to this file announces nothing
until it has been merged. And the trigger is the *workflow*, not a schedule: a
failure that happens while the notification job itself is broken is silent, which
is why the job is small and has no dependencies beyond `gh` and `curl`.

For a verdict on one run from a shell, `.github/scripts/wait-for-run.sh <id>`
blocks with `gh run watch` and prints `VERDICT: green` or `VERDICT: RED` from the
API's `status` and `conclusion`, exiting with the same answer. Piping `gh run
watch --exit-status` into `tail` does not work: the exit status belongs to
`tail`, and the lines you see are the last steps rather than the conclusion.

## Related

- [Deployment and Secrets](deployment-secrets.md) — how the payloads are rendered outside CI.
- [Configuration](production-configuration.md) — the profile and environment contract the pipeline asserts.
- [Observability](observability.md) — what the deployed stack reports once this pipeline has shipped it.
