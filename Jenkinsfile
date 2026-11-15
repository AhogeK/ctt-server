// Deploy job: it ships the tree on master and health-checks it.
//
// The merge gates live in .github/workflows/ci.yml — formatting, the full test
// suite, coverage, the secret scan, Trivy, the SBOM and the build provenance all
// run there, on a machine that is not also running production. This job builds
// only to prove the deployment still starts.
//
// Credentials: the SOPS age identity is a Jenkins file credential. Nothing is
// written to a plaintext .env — Compose reads the rendered 0440 files and the
// values below, none of which is a secret.
pipeline {
    agent any

    tools {
        jdk 'Java25'
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
                    // surfacing as sops noise inside render.sh.
                    sh 'sops --version && python3 --version && docker compose version'
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

        stage('Deploy') {
            steps {
                dir("${REPO_DIR}") {
                    sh '''
                        export SECRET_GID="${SECRET_GID:-$(id -g)}"
                        CONTAINER_NAME=ctt-server-test docker compose up -d --build app
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
