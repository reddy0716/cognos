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

    // Server & project defaults
    COGNOS_SERVER_URL = "https://cgrptmcip01.cloud.cammis.ca.gov"
    PROJECT_NAME      = "Demo"

    // Instance names (resolved to IDs dynamically)
    DEVTEST_INSTANCE  = "Cognos-DEV/TEST"
    PROD_INSTANCE     = "Cognos-PRD"

    // Label flow
    SOURCE_LABEL_ID   = "57" // <-- override at build if needed
    TEST_TARGET_LABEL = "TEST-PROMOTED-${BUILD_NUMBER}"
    PROD_TARGET_LABEL = "PROD-PROMOTED-${BUILD_NUMBER}"

    // Namespace (adjust if DEV/TEST differs)
    CAM_NAMESPACE     = "AzureAD"
  }

  options {
    disableConcurrentBuilds()
    timestamps()
    timeout(time: 45, unit: 'MINUTES')
  }

  stages {
    stage("initialize") {
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
        container('python') {
          sh '''
            set -e
            which python3
            python3 --version
          '''
        }
      }
    }

    // ---- MotioCI login (parse x-auth_token) ----
    stage('MotioCI Login') {
      steps {
        withCredentials([file(credentialsId: 'prod-credentials-json', variable: 'CREDENTIALS_FILE')]) {
          container('python') {
            script {
              sh '''
                set -euo pipefail
                cd MotioCI/api/CLI
                python3 -m pip install --user -r requirements.txt >/dev/null
              '''
              env.MOTIO_AUTH_TOKEN = sh(
                script: '''
                  set -euo pipefail
                  cd MotioCI/api/CLI
                  python3 ci-cli.py --server="${COGNOS_SERVER_URL}" login --credentialsFile "$CREDENTIALS_FILE" \
                    | awk -F': ' '/^x-auth_token:/ {print $2}' | tr -d ' \\r\\n'
                ''',
                returnStdout: true
              ).trim()
              if (!env.MOTIO_AUTH_TOKEN) { error "MotioCI token is empty" }
              echo "MotioCI login OK"
            }
          }
        }
      }
    }

    // ---- Resolve instance IDs by name (uses token) ----
    stage('Resolve Instance IDs') {
      steps {
        container('python') {
          script {
            env.DEVTEST_INSTANCE_ID = sh(
              script: '''
                set -euo pipefail
                cd MotioCI/api/CLI
                python3 ci-cli.py --server="${COGNOS_SERVER_URL}" instance ls --xauthtoken="${MOTIO_AUTH_TOKEN}" --json \
                | python3 - "${DEVTEST_INSTANCE}" <<'PY'
import sys,json
name=sys.argv[1]
d=json.load(sys.stdin)
print(next((str(x.get('id')) for x in d.get('instances',[]) if x.get('name')==name), ''), end='')
PY
              ''',
              returnStdout: true
            ).trim()

            env.PROD_INSTANCE_ID = sh(
              script: '''
                set -euo pipefail
                cd MotioCI/api/CLI
                python3 ci-cli.py --server="${COGNOS_SERVER_URL}" instance ls --xauthtoken="${MOTIO_AUTH_TOKEN}" --json \
                | python3 - "${PROD_INSTANCE}" <<'PY'
import sys,json
name=sys.argv[1]
d=json.load(sys.stdin)
print(next((str(x.get('id')) for x in d.get('instances',[]) if x.get('name')==name), ''), end='')
PY
              ''',
              returnStdout: true
            ).trim()

            if (!env.DEVTEST_INSTANCE_ID || !env.PROD_INSTANCE_ID) {
              error "Failed to resolve instance IDs (DEV/TEST='${env.DEVTEST_INSTANCE}', PROD='${env.PROD_INSTANCE}')"
            }
            echo "IDs: DEV/TEST=${env.DEVTEST_INSTANCE_ID}, PROD=${env.PROD_INSTANCE_ID}"
          }
        }
      }
    }

    // ---- DEV -> TEST ----
    stage('Preflight (DEV/TEST)') {
      steps {
        container('python') {
          sh '''
            set -euo pipefail
            cd MotioCI/api/CLI
            python3 ci-cli.py --server="${COGNOS_SERVER_URL}" project ls \
              --xauthtoken="${MOTIO_AUTH_TOKEN}" --instanceName="${DEVTEST_INSTANCE}" >/dev/null
            python3 ci-cli.py --server="${COGNOS_SERVER_URL}" label ls \
              --xauthtoken="${MOTIO_AUTH_TOKEN}" --instanceName="${DEVTEST_INSTANCE}" --projectName="${PROJECT_NAME}" \
              | grep -q " ${SOURCE_LABEL_ID} " || { echo "Label ${SOURCE_LABEL_ID} not visible in DEV/TEST"; exit 4; }
          '''
        }
      }
    }

    stage('Promote: DEV -> TEST') {
      steps {
        withCredentials([usernamePassword(credentialsId: 'cognosserviceaccount', usernameVariable: 'COGNOS_USER', passwordVariable: 'COGNOS_PASS')]) {
          container('python') {
            sh '''
              set -euo pipefail
              cd MotioCI/api/CLI
              echo "Promoting Label ID ${SOURCE_LABEL_ID} to TEST as ${TEST_TARGET_LABEL}"
              python3 ci-cli.py --server="${COGNOS_SERVER_URL}" deploy \
                --xauthtoken="${MOTIO_AUTH_TOKEN}" \
                --sourceInstanceId="${DEVTEST_INSTANCE_ID}" \
                --targetInstanceId="${DEVTEST_INSTANCE_ID}" \
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

    // ---- TEST -> PROD ----
    stage('Approve PROD') {
      steps {
        input message: "Promote TEST -> PROD? Project=${env.PROJECT_NAME}, target label=${env.PROD_TARGET_LABEL}", ok: "Proceed"
      }
    }

    stage('Preflight (find TEST label by name)') {
      steps {
        container('python') {
          script {
            env.TEST_LABEL_ID = sh(
              script: '''
                set -euo pipefail
                cd MotioCI/api/CLI
                python3 ci-cli.py --server="${COGNOS_SERVER_URL}" label ls --xauthtoken="${MOTIO_AUTH_TOKEN}" \
                  --instanceName="${DEVTEST_INSTANCE}" --projectName="${PROJECT_NAME}" --json \
                | python3 - "${TEST_TARGET_LABEL}" <<'PY'
import sys,json
name=sys.argv[1]
d=json.load(sys.stdin)
print(next((str(x.get('id')) for x in d.get('labels',[]) if x.get('name')==name), ''), end='')
PY
              ''',
              returnStdout: true
            ).trim()
            if (!env.TEST_LABEL_ID) { error "Cannot find TEST label '${env.TEST_TARGET_LABEL}' to promote" }
            echo "TEST_LABEL_ID=${env.TEST_LABEL_ID}"
          }
        }
      }
    }

    stage('Promote: TEST -> PROD') {
      steps {
        withCredentials([usernamePassword(credentialsId: 'cognosserviceaccount', usernameVariable: 'COGNOS_USER', passwordVariable: 'COGNOS_PASS')]) {
          container('python') {
            sh '''
              set -euo pipefail
              cd MotioCI/api/CLI
              echo "Promoting TEST label ID ${TEST_LABEL_ID} -> PROD as ${PROD_TARGET_LABEL}"
              python3 ci-cli.py --server="${COGNOS_SERVER_URL}" deploy \
                --xauthtoken="${MOTIO_AUTH_TOKEN}" \
                --sourceInstanceId="${DEVTEST_INSTANCE_ID}" \
                --targetInstanceId="${PROD_INSTANCE_ID}" \
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
