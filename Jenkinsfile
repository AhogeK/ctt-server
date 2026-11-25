// Deploy job: it runs the digest the publish workflow signed, on master.
//
// The merge gates live in .github/workflows/ci.yml — formatting, the full test
// suite, coverage, the secret scan, Trivy, the SBOM and the build provenance all
// run there, on a machine that is not also running production. The image is
// built and signed by .github/workflows/publish.yml; this job pulls that digest,
// checks the signature against the workflow's own identity, and never builds on
// the host that runs production.
//
// The release is the same implementation the laptop and the staging stack run
// (deploy/lib/release.sh): replace the colour that is not serving, switch the
// edge to it, watch a window that can send traffic back, and leave the previous
// colour running so a rollback is a switch rather than a deploy. What is
// different here is where the bytes come from — a verified digest — and that
// the scripts refuse to report success unless the container is running exactly
// that digest.
//
// Credentials: the SOPS age identity is a Jenkins file credential. Nothing is
// written to a plaintext .env — Compose reads the rendered 0440 files, and the
// deployment facts (origins, OAuth client id) live in deploy/prod/prod.env on
// the host, not in this job.
pipeline {
    agent any

    tools {
        jdk 'Java25'
    }

    parameters {
        // A tag can be moved between verification and start; a digest cannot,
        // which is why the job refuses anything else.
        string(name: 'IMAGE_REF',
               defaultValue: '',
               description: 'ghcr.io/ahogek/ctt-server@sha256:<digest> printed by the Publish workflow')
        // Long enough for a scrape, a token refresh and one pool cycle. Raise it
        // when the window needs to cover something slower, not to make a failure
        // disappear.
        string(name: 'OBSERVE_SECONDS',
               defaultValue: '300',
               description: 'How long traffic is watched on the new colour before the release is called')
    }

    environment {
        REPO_DIR = '/repository/ctt-server'
        CTX_PATH = '/ctt-server'
    }

    stages {

        stage('Checkout') {
            steps {
                dir("${REPO_DIR}") {
                    git url: 'https://github.com/AhogeK/ctt-server.git',
                        branch: 'master'
                }
            }
        }

        stage('Tooling') {
            steps {
                dir("${REPO_DIR}") {
                    // Checked first so a missing tool names itself instead of
                    // surfacing as sops or cosign noise inside the run. The
                    // deployment host installs them with the repository script,
                    // so the failure says where they come from.
                    sh '''#!/bin/sh
                        for tool in sops cosign curl; do
                            command -v "$tool" >/dev/null || {
                                echo "$tool is not on PATH; run scripts/install.sh --tool $tool" >&2
                                exit 1
                            }
                        done
                        sops --version && python3 --version && docker compose version && cosign version \
                            || { echo "a required build tool is not usable; see docs/ops-runbook.md" >&2; exit 1; }
                    '''
                }
            }
        }

        stage('Data plane') {
            steps {
                dir("${REPO_DIR}") {
                    // PostgreSQL and Redis are processes on this host, not
                    // containers (docs/production-data-isolation.md). A release
                    // cannot create them, so fail here rather than halfway
                    // through a switch.
                    sh '''
                        set -eu
                        for port in 5432 6379; do
                            if ! python3 -c "import socket, sys; s = socket.socket(); s.settimeout(3); sys.exit(s.connect_ex(('127.0.0.1', $port)))"; then
                                echo "127.0.0.1:$port is not accepting connections on the deployment host" >&2
                                exit 1
                            fi
                        done
                        echo "the host's PostgreSQL and Redis answer on 5432 and 6379"
                    '''
                }
            }
        }

        stage('Secrets') {
            steps {
                dir("${REPO_DIR}") {
                    withCredentials([file(credentialsId: 'ctt-sops-age-identity', variable: 'SOPS_AGE_KEY_FILE')]) {
                        sh '''
                            set -euo pipefail
                            export SECRET_GID="${SECRET_GID:-$(id -g)}"
                            # One rendered payload per host: the observability
                            # stack on this machine reads the same directory
                            # (deploy/observability/docker-compose.yaml), and a
                            # second copy is how the two drift apart.
                            ./deploy/secrets/render.sh --env prod

                            # The guard rejects a production start without these,
                            # and finding out from the container's exit code
                            # after a switch is too late.
                            for name in DB_PASSWORD REDIS_PASSWORD JWT_SECRET_KEY \
                                        OAUTH_TOKEN_ENCRYPTION_KEY TOTP_ENCRYPTION_KEY \
                                        GITHUB_CLIENT_SECRET RESEND_API_KEY; do
                                if [ ! -s "deploy/secrets/run/$name" ]; then
                                    echo "missing rendered secret: $name" >&2
                                    exit 1
                                fi
                            done
                        '''
                    }
                }
            }
        }

        // A package that only the owner can pull needs a registry credential.
        // When the credential is absent the stage says so and continues: a
        // public package needs no login, and failing here would hide that
        // choice behind a missing secret.
        stage('Registry login') {
            steps {
                script {
                    try {
                        withCredentials([usernamePassword(credentialsId: 'ctt-ghcr-registry',
                                                          usernameVariable: 'GHCR_USERNAME',
                                                          passwordVariable: 'GHCR_TOKEN')]) {
                            sh '''
                                echo "$GHCR_TOKEN" | docker login ghcr.io \
                                    --username "$GHCR_USERNAME" --password-stdin
                            '''
                        }
                    } catch (MissingCredentialsException missing) {
                        echo 'No ctt-ghcr-registry credential: the package must be publicly pullable.'
                    }
                }
            }
        }

        stage('Verify image') {
            steps {
                dir("${REPO_DIR}") {
                    sh '''
                        set -euo pipefail
                        if [ -z "${IMAGE_REF:-}" ]; then
                            echo "IMAGE_REF is empty: pass the digest printed by the Publish workflow" >&2
                            exit 1
                        fi
                        case "$IMAGE_REF" in
                            *@sha256:*) ;;
                            *) echo "IMAGE_REF must pin a digest, not a tag: $IMAGE_REF" >&2; exit 1 ;;
                        esac
                        # A registry path is lowercase by definition, and
                        # verify-image can only sign off the form the registry
                        # resolves. Recording that same string is what makes the
                        # deploy stage run the reference that was checked.
                        IMAGE_REF="$(printf '%s' "$IMAGE_REF" | tr '[:upper:]' '[:lower:]')"
                        deploy/verify-image.sh "$IMAGE_REF"
                        mkdir -p build/jenkins
                        printf '%s\n' "$IMAGE_REF" > build/jenkins/image-ref
                    '''
                }
            }
        }

        stage('Deploy') {
            steps {
                dir("${REPO_DIR}") {
                    sh '''
                        set -eu
                        export SECRET_GID="${SECRET_GID:-$(id -g)}"

                        # The edge only; colours start through the release, so a
                        # container never comes up from an unverified image.
                        ./deploy/prod/up.sh

                        if [ ! -s build/jenkins/image-ref ]; then
                            echo "the verify stage recorded no reference; a deploy without one would run whatever Compose finds" >&2
                            exit 1
                        fi
                        IMAGE_REF="$(cat build/jenkins/image-ref)"
                        export IMAGE_REF

                        serving="$(./deploy/prod/switch-colour.sh)" || { echo "cannot read which colour serves now; is the edge up?" >&2; exit 1; }
                        serving="${serving%% *}"

                        # Which colour a release means is a rule release.sh owns
                        # (--next). The job only adds the one fact it can see: a
                        # host where no colour runs needs the explicit first-release
                        # flag, or a broken edge would look like a fresh install.
                        # The window is the release: it fails, and sends traffic
                        # back, on the first probe failure, 5xx or p95 above the
                        # contract, and --image makes it check the container
                        # against the digest afterwards.
                        set -- --next --image "$IMAGE_REF" --observe "${OBSERVE_SECONDS}"
                        case "$serving" in
                            unknown) set -- --first-release "$@"
                                     echo "nothing is serving yet: this is the first release" ;;
                            blue|green) ;;
                            *) echo "cannot read which colour serves now: $serving" >&2; exit 1 ;;
                        esac
                        ./deploy/prod/release.sh "$@"
                    '''
                }
            }
        }

        stage('Report') {
            steps {
                dir("${REPO_DIR}") {
                    sh '''
                        set -euo pipefail
                        running="$(./deploy/prod/switch-colour.sh)"
                        echo "serving: $running"
                        for colour in blue green; do
                            id="$(docker ps -q -f "name=ctt-prod-app-$colour" || true)"
                            if [ -n "$id" ]; then
                                echo "$colour: $(docker inspect --format '{{.Config.Image}}' "$id")"
                            fi
                        done
                        echo "roll back with: ./deploy/prod/release.sh --rollback"
                    '''
                }
            }
        }
    }

    post {
        success {
            echo "Release passed its window. Rollback stays one command away; the previous colour is still running."
        }
        failure {
            sh '''#!/bin/sh
                for colour in blue green; do
                    docker logs "ctt-prod-app-$colour" --tail 60 2>&1 | sed "s/^/[$colour] /" || true
                done
            '''
            echo "Release failed — check Console Output above; traffic was returned to the previous colour when the window saw a breach"
        }
    }
}
