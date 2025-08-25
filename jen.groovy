// Jenkinsfile (hardened & non-interactive)
def branch = env.BRANCH_NAME ?: "DEV"
def namespace = env.NAMESPACE  ?: "dev"
def cloudName = env.CLOUD_NAME == "openshift" ? "openshift" : "kubernetes"
def workingDir = "/home/jenkins/agent"

APP_NAME="combined-devops-cognos-deployments"

pipeline {
  agent {
    kubernetes {
      yaml """
apiVersion: v1
kind: Pod
spec:
  serviceAccountName: jenkins
  volumes:
    - name: dockersock
      hostPath:
        path: /var/run/docker.sock
    - emptyDir: {}
      name: varlibcontainers
    - name: jenkins-trusted-ca-bundle
      configMap:
        name: jenkins-trusted-ca-bundle
        defaultMode: 420
        optional: true
  containers:
    - name: jnlp
      securityContext:
        privileged: true
      envFrom:
        - configMapRef:
            name: jenkins-agent-env
            optional: true
      env:
        - name: GIT_SSL_CAINFO
          value: "/etc/pki/tls/certs/ca-bundle.crt"
      volumeMounts:
        - name: jenkins-trusted-ca-bundle
          mountPath: /etc/pki/tls/certs
    - name: node
      image: registry.access.redhat.com/ubi8/nodejs-16:latest
      tty: true
      command: ["/bin/bash"]
      securityContext:
        privileged: true
      workingDir: ${workingDir}
      envFrom:
        - configMapRef:
            name: jenkins-agent-env
            optional: true
      env:
        - name: HOME
          value: ${workingDir}
        - name: BRANCH
          value: ${branch}
        - name: GIT_SSL_CAINFO
          value: "/etc/pki/tls/certs/ca-bundle.crt"
      volumeMounts:
        - name: jenkins-trusted-ca-bundle
          mountPath: /etc/pki/tls/certs
    - name: python
      image: 136299550619.dkr.ecr.us-west-2.amazonaws.com/cammisboto3:1.2.0
      tty: true
      command: ["/bin/bash"]
      securityContext:
        privileged: true
      workingDir: ${workingDir}
      envFrom:
        - configMapRef:
            name: jenkins-agent-env
            optional: true
      env:
        - name: HOME
          value: ${workingDir}
        - name: BRANCH
          value: ${branch}
        - name: GIT_SSL_CAINFO
          value: "/etc/pki/tls/certs/ca-bundle.crt"
        # Make requests trust the mounted CA bundle (no -k)
        - name: REQUESTS_CA_BUNDLE
          value: "/etc/pki/tls/certs/ca-bundle.crt"
        - name: SSL_CERT_FILE
          value: "/etc/pki/tls/certs/ca-bundle.crt"
        # Non-interactive mode signal for the CLI
        - name: CI
          value: "1"
      volumeMounts:
        - name: jenkins-trusted-ca-bundle
          mountPath: /etc/pki/tls/certs
"""
    }
  }

  environment {
    GIT_BRANCH = "${BRANCH_NAME}"
    MOTIO_SERVER = "https://cgrptmcip01.cloud.cammis.ca.gov"

    // Source/Target config
    SRC_INSTANCE_NAME = "Cognos-DEV/TEST"
    SRC_INSTANCE_ID   = "3"
    TGT_INSTANCE_NAME = "Cognos-PRD"
    TGT_INSTANCE_ID   = "1"
    PROJECT_NAME      = "Demo"
    SOURCE_LABEL_ID   = "57"
    TARGET_LABEL_NAME = "PROMOTED-20250712-115"

    // Namespace (exact id as shown in Cognos)
    NAMESPACE_ID      = "azure"
  }

  options {
    disableConcurrentBuilds()
    timestamps()
  }

  stages {
    stage("initialize") {
      steps {
        script {
          echo "Branch: ${env.GIT_BRANCH}"
          echo "Initializing Motio pipeline..."
        }
      }
    }

    stage('Check Python Availability') {
      steps {
        container('node') {
          sh '''
            set -e
            echo "Checking for Python3..."
            which python3 || true
            python3 --version || true
          '''
        }
      }
    }

    stage('Install CLI deps') {
      steps {
        container('python') {
          sh '''
            set -e
            cd MotioCI/api/CLI
            python3 -m pip install --user -r requirements.txt
            echo "Dependencies installed."
          '''
        }
      }
    }

    stage('MotioCI Login') {
      steps {
        // Preferred: a JSON credentials file credential for MotioCI login.py/ci-cli login
        withCredentials([file(credentialsId: 'prod-credentials-json', variable: 'CREDENTIALS_FILE')]) {
          container('python') {
            script {
              env.MOTIO_AUTH_TOKEN = sh(
                script: '''
                  set -e
                  cd MotioCI/api/CLI
                  # ci-cli.py prints ONLY the token to stdout
                  TOKEN=$(python3 ci-cli.py --server="$MOTIO_SERVER" --non-interactive login --credentialsFile "$CREDENTIALS_FILE")
                  echo "$TOKEN"
                ''',
                returnStdout: true
              ).trim()
              echo "MotioCI login completed - token captured."
            }
          }
        }
      }
    }

    stage('Deploy') {
      steps {
        // Cognos PROD identity to perform the import (Username/Password credential)
        withCredentials([usernamePassword(credentialsId: 'cognos-prod-service-user', usernameVariable: 'COG_USER', passwordVariable: 'COG_PASS')]) {
          container('python') {
            sh '''
              set -euo pipefail
              cd MotioCI/api/CLI

              echo "Sanity: list projects on ${TGT_INSTANCE_NAME}..."
              python3 ci-cli.py --server="$MOTIO_SERVER" project ls \
                --xauthtoken="${MOTIO_AUTH_TOKEN}" \
                --instanceName="${TGT_INSTANCE_NAME}"

              echo "Deploying label ${SOURCE_LABEL_ID} -> ${TGT_INSTANCE_NAME}/${PROJECT_NAME} as ${TARGET_LABEL_NAME}..."
              python3 ci-cli.py --server="$MOTIO_SERVER" --non-interactive deploy \
                --xauthtoken="${MOTIO_AUTH_TOKEN}" \
                --sourceInstanceId="${SRC_INSTANCE_ID}" \
                --labelId="${SOURCE_LABEL_ID}" \
                --projectName="${PROJECT_NAME}" \
                --targetInstanceId="${TGT_INSTANCE_ID}" \
                --targetLabelName="${TARGET_LABEL_NAME}" \
                --username="${COG_USER}" \
                --password="${COG_PASS}" \
                --namespaceId="${NAMESPACE_ID}"

              echo "Verifying target label presence..."
              python3 ci-cli.py --server="$MOTIO_SERVER" label ls \
                --xauthtoken="${MOTIO_AUTH_TOKEN}" \
                --instanceName="${TGT_INSTANCE_NAME}" \
                --projectName="${PROJECT_NAME}" | tee verify_labels.out

              grep -q "\\\"name\\\": \\\"${TARGET_LABEL_NAME}\\\"" verify_labels.out \
                && echo "Verified: ${TARGET_LABEL_NAME} present in ${TGT_INSTANCE_NAME}/${PROJECT_NAME}" \
                || { echo "Target label not found in ${TGT_INSTANCE_NAME}/${PROJECT_NAME}"; exit 1; }
            '''
          }
        }
      }
    }
  }

  post {
    always {
      echo "Pipeline execution finished."
    }
    success {
      echo "MotioCI pipeline completed successfully."
    }
    failure {
      echo "MotioCI pipeline failed."
    }
  }
}
