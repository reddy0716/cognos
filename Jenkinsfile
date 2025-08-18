/*
=======================================================================================
This pipeline automates Cognos promotions via MotioCI CLI with secure auth, TLS, and
traceable labels. It is parameterized for any source->target path (DEV/SIT/UAT/PRD).
=======================================================================================
*/

def workingDir = "/home/jenkins/agent"

pipeline {
  agent {
    kubernetes {
      yaml """
apiVersion: v1
kind: Pod
spec:
  serviceAccountName: jenkins
  volumes:
    - name: jenkins-trusted-ca-bundle
      configMap:
        name: jenkins-trusted-ca-bundle
        defaultMode: 420
        optional: true
  containers:
    - name: jnlp
      env:
        - name: GIT_SSL_CAINFO
          value: "/etc/pki/tls/certs/ca-bundle.crt"
      volumeMounts:
        - name: jenkins-trusted-ca-bundle
          mountPath: /etc/pki/tls/certs

    - name: python
      image: 136299550619.dkr.ecr.us-west-2.amazonaws.com/cammisboto3:1.2.0
      tty: true
      command: ["/bin/bash"]
      workingDir: ${workingDir}
      env:
        - name: HOME
          value: ${workingDir}
        - name: GIT_SSL_CAINFO
          value: "/etc/pki/tls/certs/ca-bundle.crt"
        - name: REQUESTS_CA_BUNDLE
          value: "/etc/pki/tls/certs/ca-bundle.crt"   # python requests trusts CA
        - name: SSL_CERT_FILE
          value: "/etc/pki/tls/certs/ca-bundle.crt"   # openssl trusts CA
      volumeMounts:
        - name: jenkins-trusted-ca-bundle
          mountPath: /etc/pki/tls/certs
"""
    }
  }

  options {
    disableConcurrentBuilds()
    timestamps()
  }

  parameters {
    string(name: 'MOTIO_SERVER_URL',      defaultValue: 'https://cgrptmcip01.cloud.cammis.ca.gov', description: 'MotioCI server URL')
    string(name: 'SOURCE_INSTANCE_NAME',  defaultValue: 'Cognos-DEV/TEST', description: 'Source instance (MotioCI instanceName)')
    string(name: 'TARGET_INSTANCE_NAME',  defaultValue: 'Cognos-PRD',      description: 'Target instance (MotioCI instanceName)')
    string(name: 'PROJECT_NAME',          defaultValue: 'Demo',            description: 'MotioCI project name')
    string(name: 'SOURCE_INSTANCE_ID',    defaultValue: '3',               description: 'Source instanceId (numeric)')
    string(name: 'TARGET_INSTANCE_ID',    defaultValue: '1',               description: 'Target instanceId (numeric)')
    string(name: 'LABEL_ID',              defaultValue: '57',              description: 'Label ID to promote (numeric)')
    string(name: 'TARGET_LABEL_NAME',     defaultValue: 'PROMOTED-${BUILD_TAG}', description: 'Target label name to create on target')
    string(name: 'NAMESPACE_ID',          defaultValue: 'AzureAD',         description: 'Cognos namespaceId')
  }

  environment {
    WORKDIR = "${workingDir}"
  }

  stages {
    stage('Initialize') {
      steps {
        script {
          echo "Starting MotioCI promotion: ${params.SOURCE_INSTANCE_NAME} -> ${params.TARGET_INSTANCE_NAME}"
        }
      }
    }

    stage('Check Python Availability') {
      steps {
        container('python') {
          sh '''
            set -e
            which python3
            python3 --version
            python3 -m pip --version || true
            # Show CA file presence
            ls -l /etc/pki/tls/certs/ca-bundle.crt || true
          '''
        }
      }
    }

    stage('Install MotioCI CLI Deps') {
      steps {
        container('python') {
          sh '''
            set -e
            cd MotioCI/api/CLI
            python3 -m pip install --user -r requirements.txt
          '''
        }
      }
    }

    stage('MotioCI Login') {
      steps {
        withCredentials([file(credentialsId: 'prod-credentials-json', variable: 'CREDENTIALS_FILE')]) {
          container('python') {
            script {
              env.MOTIO_AUTH_TOKEN = sh(
                script: """
                  set -e
                  cd MotioCI/api/CLI
                  python3 ci-cli.py --server="${params.MOTIO_SERVER_URL}" login --credentialsFile "$CREDENTIALS_FILE" \
                  | awk -F': ' '/Auth Token:/ {print \$2}'
                """,
                returnStdout: true
              ).trim()

              if (!env.MOTIO_AUTH_TOKEN) {
                error("Login failed: MOTIO_AUTH_TOKEN is empty")
              }
              echo "Login OK: token captured (not printed)."
            }
          }
        }
      }
    }

    stage('Create Version Label (Traceability)') {
      steps {
        container('python') {
          sh '''
            set -e
            cd MotioCI/api/CLI
            VERSION_NAME="${JOB_NAME}-${BUILD_NUMBER}"
            echo "Creating label: $VERSION_NAME on ${PROJECT_NAME} in ${SOURCE_INSTANCE_NAME}"
            python3 ci-cli.py --server="${MOTIO_SERVER_URL}" label create \
              --xauthtoken="${MOTIO_AUTH_TOKEN}" \
              --instanceName="${SOURCE_INSTANCE_NAME}" \
              --projectName="${PROJECT_NAME}" \
              --name="$VERSION_NAME" \
              --versionedItemIds="[]"
          '''
        }
      }
    }

    stage('Deploy (Promote)') {
      steps {
        withCredentials([string(credentialsId: 'cognos-cam-passport-id', variable: 'CAM_PASSPORT_ID')]) {
          container('python') {
            sh '''
              set -e
              cd MotioCI/api/CLI

              echo "Verifying access to target project before deploy..."
              python3 ci-cli.py --server="${MOTIO_SERVER_URL}" project ls \
                --xauthtoken="${MOTIO_AUTH_TOKEN}" \
                --instanceName="${TARGET_INSTANCE_NAME}" \
                | grep -q "${PROJECT_NAME}"

              echo "Running deploy..."
              python3 ci-cli.py --server="${MOTIO_SERVER_URL}" deploy \
                --xauthtoken="${MOTIO_AUTH_TOKEN}" \
                --sourceInstanceId="${SOURCE_INSTANCE_ID}" \
                --targetInstanceId="${TARGET_INSTANCE_ID}" \
                --labelId="${LABEL_ID}" \
                --projectName="${PROJECT_NAME}" \
                --targetLabelName="${TARGET_LABEL_NAME}" \
                --camPassportId="${CAM_PASSPORT_ID}" \
                --namespaceId="${NAMESPACE_ID}"

              echo "Deploy CLI exit code OK."
            '''
          }
        }
      }
    }

    stage('Post-Deploy Verification') {
      steps {
        container('python') {
          sh '''
            set -e
            cd MotioCI/api/CLI
            echo "Checking target labels contain ${TARGET_LABEL_NAME}..."
            python3 ci-cli.py --server="${MOTIO_SERVER_URL}" label ls \
              --xauthtoken="${MOTIO_AUTH_TOKEN}" \
              --instanceName="${TARGET_INSTANCE_NAME}" \
              --projectName="${PROJECT_NAME}" \
              | grep -q "${TARGET_LABEL_NAME}"
            echo "Verification OK: label ${TARGET_LABEL_NAME} present in target."
          '''
        }
      }
    }
  }

  post {
    success {
      echo "✅ MotioCI promotion completed and verified: ${params.SOURCE_INSTANCE_NAME} -> ${params.TARGET_INSTANCE_NAME}"
    }
    failure {
      echo "❌ MotioCI promotion failed. Check stage logs above."
    }
    always {
      echo "Pipeline finished."
    }
  }
}
