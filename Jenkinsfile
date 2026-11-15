// Deploy job: it runs the digest the publish workflow signed, on master.
//
// The merge gates live in .github/workflows/ci.yml — formatting, the full test
// suite, coverage, the secret scan, Trivy, the SBOM and the build provenance all
// run there, on a machine that is not also running production. The image is
// built and signed by .github/workflows/publish.yml; this job pulls that digest,
// checks the signature against the workflow's own identity, and never builds on
// the host that runs production.
//
// Credentials: the SOPS age identity is a Jenkins file credential. Nothing is
// written to a plaintext .env — Compose reads the rendered 0440 files and the
// values below, none of which is a secret.
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
    }

    environment {
        REPO_DIR                = '/repository/ctt-server'
        CTX_PATH                = '/ctt-server'

        APP_PORT                = '8080'
        APP_EXTERNAL_PORT       = '8004'
        MANAGEMENT_PORT         = '8081'
        POSTGRES_EXTERNAL_PORT  = '15432'
        REDIS_EXTERNAL_PORT     = '16379'
        MAIL_SMTP_EXTERNAL_PORT = '1025'
        MAIL_UI_EXTERNAL_PORT   = '8025'
        SPRING_PROFILES_ACTIVE  = 'local'
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
                    // surfacing as sops or cosign noise inside the run.
                    sh 'sops --version && python3 --version && docker compose version && cosign version'
                }
            }
        }

        stage('Configure') {
            steps {
                dir("${REPO_DIR}") {
                    // The template only carries non-secret defaults; the rendered
                    // secrets override its datasource and Redis passwords.
                    sh '''
                        LOCAL_YAML=src/main/resources/application-local.yaml
                        if [ ! -f "$LOCAL_YAML" ]; then
                            cp "$LOCAL_YAML.template" "$LOCAL_YAML"
                            echo "application-local.yaml generated from template."
                        else
                            echo "application-local.yaml already exists, skipping."
                        fi
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
                            ./deploy/secrets/render.sh --env dev

                            for name in DB_PASSWORD REDIS_PASSWORD JWT_SECRET_KEY \
                                        OAUTH_TOKEN_ENCRYPTION_KEY RESEND_API_KEY; do
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

        stage('Infra Up') {
            steps {
                dir("${REPO_DIR}") {
                    sh '''
                        export SECRET_GID="${SECRET_GID:-$(id -g)}"
                        docker compose up -d postgres redis mailpit
                    '''
                    sh '''
                        docker network connect ctt-server_default jenkins-blueocean 2>/dev/null || true

                        echo "Waiting for postgres to be healthy..."
                        for i in $(seq 1 20); do
                            STATUS=$(docker inspect --format="{{.State.Health.Status}}" ctt-postgres 2>/dev/null || echo "none")
                            if [ "$STATUS" = "healthy" ]; then
                                echo "✅ Postgres is ready"
                                exit 0
                            fi
                            echo "Waiting... ($i/20) [$STATUS]"
                            sleep 3
                        done
                        echo "❌ Postgres never became healthy"
                        exit 1
                    '''
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
                        deploy/verify-image.sh "$IMAGE_REF"
                    '''
                }
            }
        }

        stage('Deploy') {
            steps {
                dir("${REPO_DIR}") {
                    sh '''
                        export SECRET_GID="${SECRET_GID:-$(id -g)}"
                        # --no-build is the point: if the image is missing, the
                        # deploy fails instead of silently substituting a
                        # locally built one nobody verified.
                        CONTAINER_NAME=ctt-server-test IMAGE_REF="$IMAGE_REF" \
                            docker compose up -d --no-build app
                    '''
                }
            }
        }

        stage('Health Check') {
            steps {
                dir("${REPO_DIR}") {
                    sh '''
                        CONTAINER=ctt-server-test
                        echo "Waiting for app container to be healthy..."
                        for i in $(seq 1 24); do
                            STATUS=$(docker inspect --format="{{.State.Health.Status}}" "$CONTAINER" 2>/dev/null || echo "none")
                            if [ "$STATUS" = "healthy" ]; then
                                echo "✅ Service is UP"
                                exit 0
                            fi
                            echo "Waiting... ($i/24) [$STATUS]"
                            sleep 5
                        done
                        echo "❌ Health check timed out"
                        docker logs "$CONTAINER" --tail 50 || true
                        exit 1
                    '''
                }
            }
        }
    }

    post {
        success {
            echo "✅ Deploy success → http://localhost:8004/ctt-server/swagger-ui.html"
        }
        failure {
            sh 'docker logs ctt-server-test --tail 100 || true'
            echo "❌ Deploy failed — check Console Output above"
        }
    }
}
