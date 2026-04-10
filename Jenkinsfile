pipeline {
    agent any

    tools {
        jdk 'Java25'
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

        stage('Configure') {
            steps {
                dir("${REPO_DIR}") {
                    sh '''
                        cat > .env << 'EOF'
POSTGRES_DB=ctt_server
POSTGRES_USER=ctt
POSTGRES_PASSWORD=ctt_local_pass
POSTGRES_EXTERNAL_PORT=15432
REDIS_PASSWORD=ctt_redis_pass
REDIS_EXTERNAL_PORT=16379
MAIL_SMTP_EXTERNAL_PORT=1025
MAIL_UI_EXTERNAL_PORT=8025
APP_PORT=8004
APP_EXTERNAL_PORT=8004
JWT_SECRET_KEY=CI_TEST_SECRET_KEY_MUST_BE_AT_LEAST_256_BITS_LONG_REPLACE_IN_PROD
MAIL_FROM_ADDRESS=noreply@ci.test
MAIL_FROM_NAME=CTT CI
FRONTEND_BASE_URL=http://localhost:5173
SPRING_PROFILES_ACTIVE=local
EOF
                    '''
                    sh '''
                        LOCAL_YAML=src/main/resources/application-local.yaml
                        TEMPLATE=src/main/resources/application-local.yaml.template
                        if [ ! -f "$LOCAL_YAML" ]; then
                            cp "$TEMPLATE" "$LOCAL_YAML"
                            echo "application-local.yaml generated from template."
                        else
                            echo "application-local.yaml already exists, skipping."
                        fi
                    '''
                }
            }
        }

        stage('Infra Up') {
            steps {
                dir("${REPO_DIR}") {
                    sh 'docker compose up -d postgres redis mailpit'
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
                    sh 'docker compose up -d --build app'
                }
            }
        }

        stage('Health Check') {
            steps {
                dir("${REPO_DIR}") {
                    sh '''
                        echo "Waiting for app container to be healthy..."
                        for i in $(seq 1 24); do
                            STATUS=$(docker inspect --format="{{.State.Health.Status}}" ctt-server 2>/dev/null || echo "none")
                            if [ "$STATUS" = "healthy" ]; then
                                echo "✅ Service is UP"
                                exit 0
                            fi
                            echo "Waiting... ($i/24) [$STATUS]"
                            sleep 5
                        done
                        echo "❌ Health check timed out"
                        docker logs ctt-server --tail 50 || true
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
            sh 'docker logs ctt-server --tail 100 || true'
            echo "❌ Deploy failed — check Console Output above"
        }
    }
}
