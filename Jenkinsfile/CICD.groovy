pipeline {
    agent {
        docker {
            image 'ramagiri/maven-docker-agent:v1'
            args '-u root -v /var/run/docker.sock:/var/run/docker.sock' // Mount Docker socket
        }
    }

    environment {
        SONAR_URL = 'http://<your-sonarqube-ip>:9000'
        DOCKER_IMAGE = "jithendarramagiri1998/ultimate-cicd:${BUILD_NUMBER}"
        GIT_REPO_NAME = "Jenkins-Zero-To-Hero"
        GIT_USER_NAME = "<your-github-username>" // Replace with actual GitHub username
    }

    stages {

        stage('Checkout') {
            steps {
                echo 'Checking out source code...'
                checkout scm
            }
        }

        stage('Build and Test') {
            steps {
                echo 'Building and testing the application...'
                sh 'cd java-maven-sonar-argocd-helm-k8s/spring-boot-app && mvn clean package'
            }
        }

        stage('Static Code Analysis') {
            environment {
                // SonarQube server URL
                SONAR_URL = 'http://<your-sonarqube-ip>:9000'
            }
            steps {
                withCredentials([string(credentialsId: 'sonarqube', variable: 'SONAR_AUTH_TOKEN')]) {
                    sh """
                        cd java-maven-sonar-argocd-helm-k8s/spring-boot-app && \
                        mvn sonar:sonar \
                        -Dsonar.login=$SONAR_AUTH_TOKEN \
                        -Dsonar.host.url=$SONAR_URL
                    """
                }
            }
        }

        stage('Build and Push Docker Image') {
            environment {
                REGISTRY_CREDENTIALS = credentials('docker-cred')
            }
            steps {
                script {
                    echo 'Building Docker image...'
                    sh """
                        cd java-maven-sonar-argocd-helm-k8s/spring-boot-app && \
                        docker build -t ${DOCKER_IMAGE} .
                    """

                    echo 'Pushing Docker image to registry...'
                    docker.withRegistry('https://index.docker.io/v1/', 'docker-cred') {
                        docker.image("${DOCKER_IMAGE}").push()
                    }
                }
            }
        }

        stage('Update Deployment File and Push to GitHub') {
            environment {
                GIT_CREDENTIALS = credentials('github') // GitHub Personal Access Token (PAT)
            }
            steps {
                script {
                    echo 'Updating Kubernetes deployment file with new image tag...'
                    sh """
                        cd java-maven-sonar-argocd-helm-k8s/spring-boot-app-manifests && \
                        sed -i 's/replaceImageTag/${BUILD_NUMBER}/g' deployment.yml
                    """

                    echo 'Configuring Git and committing changes...'
                    sh """
                        git config --global user.email "<your-email@example.com>" && \
                        git config --global user.name "${GIT_USER_NAME}" && \
                        cd java-maven-sonar-argocd-helm-k8s/spring-boot-app-manifests && \
                        git add deployment.yml && \
                        git commit -m "Update deployment image to version ${BUILD_NUMBER}" && \
                        git push https://${GIT_CREDENTIALS}@github.com/${GIT_USER_NAME}/${GIT_REPO_NAME}.git HEAD:main
                    """
                }
            }
        }
    }
}

