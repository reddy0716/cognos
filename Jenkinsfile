/*
=======================================================================================
If you have suggestions for improvement, contact DevOps so we can fold changes
into the template. If you don't want this file updated automatically, indicate so.
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

    // Instance names (for readability)
    DEVTEST_INSTANCE  = "Cognos-DEV/TEST"
    PROD_INSTANCE     = "Cognos-PRD"

    // Instance IDs (to avoid CLI prompts)
    DEVTEST_INSTANCE_ID = "3"
    PROD_INSTANCE_ID    = "1"

    // Label flow
    SOURCE_LABEL_ID   = "57" // starting label in DEV
    TEST_TARGET_LABEL = "TEST-PROMOTED-${BUILD_NUMBER}"
    PROD_TARGET_LABEL = "PROD-PROMOTED-${BUILD_NUMBER}"

    // Namespaces (IDs are exact strings required by Cognos). We'll print DEV namespaces to confirm.
    CAM_NAMESPACE_DEV = "AzureAD" // adjust after "List Namespaces (DEV)" output if needed (e.g., "Azure AD")
    CAM_NAMESPACE_PRD = "AzureAD"
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
          echo "Instances: DEV/TEST='${env.DEVTEST_INSTANCE}' (ID=${env.DEVTEST_INSTANCE_ID})  PROD='${env.PROD_INSTANCE}' (ID=${env.PROD_INSTANCE_ID})"
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

    // ---- Discover the real namespace IDs in DEV/TEST (so --namespaceId is correct) ----
    stage('List Namespaces (DEV)') {
      steps {
        container('python') {
          sh '''
            set -euo pipefail
            export PATH="$HOME/.local/bin:$PATH"
            cd MotioCI/api/CLI
            python3 - <<'PY'
import requests, json, queries
CI_URL = "${COGNOS_SERVER_URL}"
GRAPH_URL = CI_URL + "/graphql"
headers = {"x-auth-token": "${MOTIO_AUTH_TOKEN_DEV}"}
q = queries.GET_VERSIONED_NAMESPACES
vars = {"id": int(${DEVTEST_INSTANCE_ID})}
r = requests.post(GRAPH_URL, headers=headers, json={"query": q, "variables": vars}, verify=False)
print("=== DEV/TEST Namespaces ===")
try:
    data = r.json()["data"]["instance"]["namespaces"]
    for ns in data:
        print(json.dumps(ns))  # includes the exact 'id' to use for --namespaceId
except Exception:
    print("Failed to parse namespaces. Raw response:")
    print(r.text)
PY
          '''
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
                --sourceInstanceId="${DEVTEST_INSTANCE_ID}" \
                --targetInstanceId="${DEVTEST_INSTANCE_ID}" \
                --labelId="${SOURCE_LABEL_ID}" \
                --projectName="${PROJECT_NAME}" \
                --targetLabelName="${TEST_TARGET_LABEL}" \
                --username "$COGNOS_USER" \
                --password "$COGNOS_PASS" \
                --namespaceId "${CAM_NAMESPACE_DEV}" 2>deploy_test.err || true

              echo "---- DEV->TEST deploy stderr (for diagnostics) ----"
              cat deploy_test.err || true
              echo "---------------------------------------------------"

              if grep -qiE "denied|forbidden|401|403|DEPLOYMENT_AUTHENTICATION_FAILED|failed to authenticate" deploy_test.err; then
                echo "TEST DEPLOY AUTH ERROR (check namespaceId and service account in DEV)."
                exit 12
              fi

              if grep -qiE "error|exception|traceback" deploy_test.err; then
                echo "TEST DEPLOY FAILED:"
                exit 13
              fi
            '''
          }
        }
      }
    }

    stage('Verify TEST Label Created') {
      steps {
        container('python') {
          sh '''
            set -euo pipefail
            export PATH="$HOME/.local/bin:$PATH"
            cd MotioCI/api/CLI

            echo "Verifying ${TEST_TARGET_LABEL} exists in DEV/TEST..."
            python3 ci-cli.py --server="${COGNOS_SERVER_URL}" label ls \
              --xauthtoken="${MOTIO_AUTH_TOKEN_DEV}" \
              --instanceName="${DEVTEST_INSTANCE}" \
              --projectName="${PROJECT_NAME}" \
              --labelName="${TEST_TARGET_LABEL}" | grep -q "${TEST_TARGET_LABEL}"
          '''
        }
      }
    }

    stage('Approval Before PROD') {
      steps {
        timeout(time: 1, unit: 'HOURS') {
          input message: "Approve promotion from TEST to PROD?", ok: "Promote to PROD"
        }
      }
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

    // (Optional) List PROD namespaces if you’re unsure of the exact ID string
    stage('List Namespaces (PROD)') {
      steps {
        container('python') {
          sh '''
            set -euo pipefail
            export PATH="$HOME/.local/bin:$PATH"
            cd MotioCI/api/CLI
            python3 - <<'PY'
import requests, json, queries
CI_URL = "${COGNOS_SERVER_URL}"
GRAPH_URL = CI_URL + "/graphql"
headers = {"x-auth-token": "${MOTIO_AUTH_TOKEN_PROD}"}
q = queries.GET_VERSIONED_NAMESPACES
vars = {"id": int(${PROD_INSTANCE_ID})}
r = requests.post(GRAPH_URL, headers=headers, json={"query": q, "variables": vars}, verify=False)
print("=== PROD Namespaces ===")
try:
    data = r.json()["data"]["instance"]["namespaces"]
    for ns in data:
        print(json.dumps(ns))
except Exception:
    print("Failed to parse namespaces. Raw response:")
    print(r.text)
PY
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
                --sourceInstanceId="${DEVTEST_INSTANCE_ID}" \
                --targetInstanceId="${PROD_INSTANCE_ID}" \
                --labelId="${TEST_LABEL_ID}" \
                --projectName="${PROJECT_NAME}" \
                --targetLabelName="${PROD_TARGET_LABEL}" \
                --username "$COGNOS_USER" \
                --password "$COGNOS_PASS" \
                --namespaceId "${CAM_NAMESPACE_PRD}" 2>deploy_prod.err || true

              echo "---- TEST->PROD deploy stderr (for diagnostics) ----"
              cat deploy_prod.err || true
              echo "----------------------------------------------------"

              if grep -qiE "denied|forbidden|401|403|DEPLOYMENT_AUTHENTICATION_FAILED|failed to authenticate" deploy_prod.err; then
                echo "PROD DEPLOY AUTH ERROR (check namespaceId and service account in PROD)."
                exit 22
              fi

              if grep -qiE "error|exception|traceback" deploy_prod.err; then
                echo "PROD DEPLOY FAILED:"
                exit 23
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
