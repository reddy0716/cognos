/*
=======================================================================================
This file is being updated constantly by the DevOps team to introduce new enhancements
based on the template.  If you have suggestions for improvement,
please contact the DevOps team so that we can incorporate the changes into the
template.  In the meantime, if you have made changes here or don't want this file to be
updated, please indicate so at the beginning of this file.
=======================================================================================
*/

def branch     = env.BRANCH_NAME ?: "DEV"
def namespace  = env.NAMESPACE   ?: "dev"
def cloudName  = env.CLOUD_NAME == "openshift" ? "openshift" : "kubernetes"
def workingDir = "/home/jenkins/agent"

APP_NAME = "combined-devops-cognos-deployments"

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
      hostPath: { path: /var/run/docker.sock }
    - emptyDir: {}
      name: varlibcontainers
    - name: jenkins-trusted-ca-bundle
      configMap:
        name: jenkins-trusted-ca-bundle
        defaultMode: 420
        optional: true
  containers:
    - name: jnlp
      securityContext: { privileged: true }
      envFrom:
        - configMapRef: { name: jenkins-agent-env, optional: true }
      env:
        - { name: GIT_SSL_CAINFO, value: "/etc/pki/tls/certs/ca-bundle.crt" }
      volumeMounts:
        - { name: jenkins-trusted-ca-bundle, mountPath: /etc/pki/tls/certs }
    - name: node
      image: registry.access.redhat.com/ubi8/nodejs-16:latest
      tty: true
      command: ["/bin/bash"]
      securityContext: { privileged: true }
      workingDir: ${workingDir}
      envFrom:
        - configMapRef: { name: jenkins-agent-env, optional: true }
      env:
        - { name: HOME,   value: ${workingDir} }
        - { name: BRANCH, value: ${branch} }
        - { name: GIT_SSL_CAINFO, value: "/etc/pki/tls/certs/ca-bundle.crt" }
      volumeMounts:
        - { name: jenkins-trusted-ca-bundle, mountPath: /etc/pki/tls/certs }
    - name: python
      image: 136299550619.dkr.ecr.us-west-2.amazonaws.com/cammisboto3:1.2.0
      tty: true
      command: ["/bin/bash"]
      securityContext: { privileged: true }
      workingDir: ${workingDir}
      envFrom:
        - configMapRef: { name: jenkins-agent-env, optional: true }
      env:
        - { name: HOME,   value: ${workingDir} }
        - { name: BRANCH, value: ${branch} }
        - { name: GIT_SSL_CAINFO,     value: "/etc/pki/tls/certs/ca-bundle.crt" }
        - { name: REQUESTS_CA_BUNDLE, value: "/etc/pki/tls/certs/ca-bundle.crt" }
      volumeMounts:
        - { name: jenkins-trusted-ca-bundle, mountPath: /etc/pki/tls/certs }
"""
    }
  }

  environment {
    GIT_BRANCH        = "${BRANCH_NAME}"

    // MotioCI server & project
    COGNOS_SERVER_URL = "https://cgrptmcip01.cloud.cammis.ca.gov"
    PROJECT_NAME      = "Demo"

    // Instance names
    DEVTEST_INSTANCE  = "Cognos-DEV/TEST"
    PROD_INSTANCE     = "Cognos-PRD"

    // Label flow
    SOURCE_LABEL_ID   = "57" // <-- override at build if needed
    TEST_TARGET_LABEL = "TEST-PROMOTED-${BUILD_NUMBER}"
    PROD_TARGET_LABEL = "PROD-PROMOTED-${BUILD_NUMBER}"

    // Cognos namespace
    CAM_NAMESPACE     = "AzureAD"
  }

  options {
    disableConcurrentBuilds()
    timestamps()
    timeout(time: 45, unit: 'MINUTES')
  }

  stages {
    stage("Initialize") {
      steps {
        script {
          echo "Branch: ${env.GIT_BRANCH}"
          echo "Project: ${env.PROJECT_NAME}"
          echo "Instances: DEV/TEST='${env.DEVTEST_INSTANCE}' PROD='${env.PROD_INSTANCE}'"
          echo "Source Label ID (DEV): ${env.SOURCE_LABEL_ID}"
        }
      }
    }

    stage('Check Python Availability') {
      steps {
        container('node') {
          sh '''
            echo "Checking for Python3..."
            which python3 || echo "Python3 is NOT installed"
            python3 --version || echo "Unable to get Python version"
          '''
        }
      }
    }

    // ---- MotioCI login (DEV/TEST) ----
    stage('MotioCI Login (DEV)') {
      steps {
        withCredentials([file(credentialsId: 'motio-dev-credentials-json', variable: 'DEV_CRED_FILE')]) {
          container('python') {
            script {
              sh '''
                set -euo pipefail
                export PATH="$HOME/.local/bin:$PATH"
                cd MotioCI/api/CLI
                python3 -m pip install --user -r requirements.txt >/dev/null || true
                python3 -m pip install --user "python-dateutil>=2.8.2" >/dev/null 2>&1 || true
              '''
              env.MOTIO_AUTH_TOKEN_DEV = sh(
                script: '''
                  set -euo pipefail
                  export PATH="$HOME/.local/bin:$PATH"
                  cd MotioCI/api/CLI
                  python3 ci-cli.py --server="${COGNOS_SERVER_URL}" login --credentialsFile "$DEV_CRED_FILE" \
                    | awk -F': ' '/^x-auth_token:/ {print $2}' | tr -d ' \\r\\n'
                ''',
                returnStdout: true
              ).trim()
              if (!env.MOTIO_AUTH_TOKEN_DEV) { error "MotioCI DEV token is empty" }
              echo "MotioCI DEV login OK (len=${env.MOTIO_AUTH_TOKEN_DEV.size()})"
            }
          }
        }
      }
    }

    // ---- MotioCI login (PROD) ----
    stage('MotioCI Login (PROD)') {
      steps {
        withCredentials([file(credentialsId: 'motio-prod-credentials-json', variable: 'PROD_CRED_FILE')]) {
          container('python') {
            script {
              env.MOTIO_AUTH_TOKEN_PROD = sh(
                script: '''
                  set -euo pipefail
                  export PATH="$HOME/.local/bin:$PATH"
                  cd MotioCI/api/CLI
                  python3 ci-cli.py --server="${COGNOS_SERVER_URL}" login --credentialsFile "$PROD_CRED_FILE" \
                    | awk -F': ' '/^x-auth_token:/ {print $2}' | tr -d ' \\r\\n'
                ''',
                returnStdout: true
              ).trim()
              if (!env.MOTIO_AUTH_TOKEN_PROD) { error "MotioCI PROD token is empty" }
              echo "MotioCI PROD login OK (len=${env.MOTIO_AUTH_TOKEN_PROD.size()})"
            }
          }
        }
      }
    }

    stage('Preflight (DEV/TEST)') {
      steps {
        container('python') {
          sh '''
            set -euo pipefail
            export PATH="$HOME/.local/bin:$PATH"
            cd MotioCI/api/CLI

            echo "Checking label visibility for ${PROJECT_NAME} in ${DEVTEST_INSTANCE}..."
            python3 ci-cli.py --server="${COGNOS_SERVER_URL}" label ls \
              --xauthtoken="${MOTIO_AUTH_TOKEN_DEV}" \
              --instanceName="${DEVTEST_INSTANCE}" \
              --projectName="${PROJECT_NAME}" >/dev/null

            echo "Ensuring target TEST label does not already exist..."
            ! python3 ci-cli.py --server="${COGNOS_SERVER_URL}" label ls \
              --xauthtoken="${MOTIO_AUTH_TOKEN_DEV}" \
              --instanceName="${DEVTEST_INSTANCE}" \
              --projectName="${PROJECT_NAME}" \
              --labelName="${TEST_TARGET_LABEL}" | grep -q "${TEST_TARGET_LABEL}" || { echo "Label ${TEST_TARGET_LABEL} already exists"; exit 1; }
          '''
        }
      }
    }

    stage('Promote DEV to TEST') {
      steps {
        withCredentials([usernamePassword(credentialsId: 'Cognosserviceaccount', usernameVariable: 'COGNOS_USER', passwordVariable: 'COGNOS_PASS')]) {
          container('python') {
            sh '''
              set -euo pipefail
              export PATH="$HOME/.local/bin:$PATH"
              cd MotioCI/api/CLI

              echo "Promoting DEV -> TEST as ${TEST_TARGET_LABEL}"
              python3 ci-cli.py --server="${COGNOS_SERVER_URL}" deploy \
                --xauthtoken="${MOTIO_AUTH_TOKEN_DEV}" \
                --sourceInstanceName="${DEVTEST_INSTANCE}" \
                --targetInstanceName="${DEVTEST_INSTANCE}" \
                --labelId="${SOURCE_LABEL_ID}" \
                --projectName="${PROJECT_NAME}" \
                --targetLabelName="${TEST_TARGET_LABEL}" \
                --username "$COGNOS_USER" \
                --password "$COGNOS_PASS" \
                --namespaceId "${CAM_NAMESPACE}" 2>deploy_test.err || true

              if grep -qiE "denied|forbidden|401|403" deploy_test.err; then
                echo "TEST ACCESS DENIED:"; cat deploy_test.err; exit 12
              fi
            '''
          }
        }
      }
    }

    stage('Approval Before PROD') {
      steps { input message: "Approve promotion to PROD?" }
    }

    stage('Resolve TEST Label ID') {
      steps {
        container('python') {
          sh '''
            set -euo pipefail
            export PATH="$HOME/.local/bin:$PATH"
            cd MotioCI/api/CLI

            echo "Looking up TEST label id for name: ${TEST_TARGET_LABEL}"
            OUT=$(python3 ci-cli.py --server="${COGNOS_SERVER_URL}" label ls \
                    --xauthtoken="${MOTIO_AUTH_TOKEN_DEV}" \
                    --instanceName="${DEVTEST_INSTANCE}" \
                    --projectName="${PROJECT_NAME}" \
                    --labelName="${TEST_TARGET_LABEL}" || true)

            TEST_LABEL_ID=$(printf "%s" "$OUT" | grep -Eo 'id[^0-9]*[[:space:]]*([0-9]+)' | grep -Eo '[0-9]+' | head -1)
            [ -n "$TEST_LABEL_ID" ] || { echo "Failed to parse TEST label id. Output:"; echo "$OUT"; exit 6; }

            echo "Resolved TEST_LABEL_ID=$TEST_LABEL_ID"
            echo "$TEST_LABEL_ID" > ../TEST_LABEL_ID.txt
          '''
        }
      }
    }

    stage('Promote TEST to PROD') {
      steps {
        withCredentials([usernamePassword(credentialsId: 'Cognosserviceaccount', usernameVariable: 'COGNOS_USER', passwordVariable: 'COGNOS_PASS')]) {
          container('python') {
            sh '''
              set -euo pipefail
              export PATH="$HOME/.local/bin:$PATH"
              cd MotioCI/api/CLI
              TEST_LABEL_ID=$(cat ../TEST_LABEL_ID.txt)

              echo "Promoting TEST label ID ${TEST_LABEL_ID} -> PROD as ${PROD_TARGET_LABEL}"
              python3 ci-cli.py --server="${COGNOS_SERVER_URL}" deploy \
                --xauthtoken="${MOTIO_AUTH_TOKEN_PROD}" \
                --sourceInstanceName="${DEVTEST_INSTANCE}" \
                --targetInstanceName="${PROD_INSTANCE}" \
                --labelId="${TEST_LABEL_ID}" \
                --projectName="${PROJECT_NAME}" \
                --targetLabelName="${PROD_TARGET_LABEL}" \
                --username "$COGNOS_USER" \
                --password "$COGNOS_PASS" \
                --namespaceId "${CAM_NAMESPACE}" 2>deploy_prod.err || true

              if grep -qiE "denied|forbidden|401|403" deploy_prod.err; then
                echo "PROD ACCESS DENIED:"; cat deploy_prod.err; exit 12
              fi
            '''
          }
        }
      }
    }
  }

  post {
    always  { echo "Pipeline execution finished." }
    success { echo "DEV->TEST and TEST->PROD promotion completed successfully." }
    failure { echo "Pipeline failed." }
  }
}
