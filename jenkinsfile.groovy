/*
=======================================================================================
This file is being updated constantly by the DevOps team to introduce new enhancements
based on the template.  If you have suggestions for improvement,
please contact the DevOps team so that we can incorporate the changes into the
template.  In the meantime, if you have made changes here or don't want this file to be
updated, please indicate so at the beginning of this file.
=======================================================================================
*/

//variables from ibm template
def branch = env.BRANCH_NAME ?: "DEV"
def namespace = env.NAMESPACE  ?: "dev"
def cloudName = env.CLOUD_NAME == "openshift" ? "openshift" : "kubernetes"
def workingDir = "/home/jenkins/agent"

APP_NAME="combined-devops-cognos-deployments"

// -------- helpers --------
@NonCPS
def nsForTarget(String inst) { return (inst == 'Cognos-PRD') ? 'AzureAD' : 'AzureAD' } // change if DEV/TEST uses different CAM namespace

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
              securityContext:
                privileged: true
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
              securityContext:
                privileged: true
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
                - name: REQUESTS_CA_BUNDLE
                  value: "/etc/pki/tls/certs/ca-bundle.crt"
              volumeMounts:
                - name: jenkins-trusted-ca-bundle
                  mountPath: /etc/pki/tls/certs
      """
    }
  }

  environment  {
    GIT_BRANCH = "${BRANCH_NAME}"

    // Static defaults that make this runnable without edits
    COGNOS_SERVER_URL = "https://cgrptmcip01.cloud.cammis.ca.gov"
    PROJECT_NAME      = "Demo"

    // Instance names (keep your originals)
    DEVTEST_INSTANCE  = "Cognos-DEV/TEST"
    PROD_INSTANCE     = "Cognos-PRD"

    // Label flow (DEV -> TEST -> PROD)
    SOURCE_LABEL_ID   = "57"                            // change at build time if needed
    TEST_TARGET_LABEL = "TEST-PROMOTED-${BUILD_NUMBER}"
    PROD_TARGET_LABEL = "PROD-PROMOTED-${BUILD_NUMBER}"

    // Namespaces (adjust if DEV/TEST differs)
    CAM_NAMESPACE_TEST = "AzureAD"
    CAM_NAMESPACE_PROD = "AzureAD"

    // Fallback service account (used ONLY if Jenkins cred isn't present)
    // You gave these values; included as fallback so this runs without Jenkins changes.
    // Move them to a Jenkins credential when practical.
    FALLBACK_COGNOS_USER = "CMarksSS01@intra.dhs.ca.gov"
    FALLBACK_COGNOS_PASS = "Service@2024DHCS"
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
          echo "Project: ${env.PROJECT_NAME}"
          echo "Instances: DEV/TEST='${env.DEVTEST_INSTANCE}', PROD='${env.PROD_INSTANCE}'"
          echo "Source Label ID (DEV): ${env.SOURCE_LABEL_ID}"
        }
      }
    }

    stage('Check Python Availability') {
      steps { 
        container('python') {
          sh '''
            set -e
            echo "Checking for Python3..."
            which python3
            python3 --version
          '''
        }
      }
    }

    // ========= MotioCI TOKENS via API KEY JSON =========
    // The prod-credentials-json contains arrays like:
    //  [ {"instanceId":"3","apiKey":"..."} ]  (DEV/TEST)
    //  [ {"instanceId":"1","apiKey":"..."} ]  (PROD)

    stage('MotioCI Login (DEV/TEST token via API key)') {
      steps {
        withCredentials([file(credentialsId: 'prod-credentials-json', variable: 'APIKEY_FILE')]) {
          container('python') {
            script {
              env.MOTIO_TOKEN_DEV = sh(
                script: '''
                  set -euo pipefail
                  cd MotioCI/api/CLI
                  python3 -m pip install --user -r requirements.txt >/dev/null
                  # pick apiKey for instanceId=3 (DEV/TEST)
                  apikey=$(python3 - <<'PY' "$APIKEY_FILE"
import json,sys,os
data=json.load(open(sys.argv[1],"r"))
# file may contain one object or array; normalize to list
if isinstance(data, dict): data=[data]
key=next((x.get("apiKey") for x in data if str(x.get("instanceId"))=="3"), "")
print(key,end="")
PY
)
                  [ -n "$apikey" ] || { echo "No DEV/TEST apiKey found in prod-credentials-json"; exit 1; }
                  tok=$(python3 ci-cli.py --server="${COGNOS_SERVER_URL}" login --apiKey "$apikey" --instanceId "3" \
                        | awk -F': ' '/Auth Token:/ {print $2}' | tr -d ' \\r\\n')
                  [ -n "$tok" ] || { echo "Empty MotioCI token (DEV/TEST)"; exit 1; }
                  echo -n "$tok"
                ''',
                returnStdout: true
              ).trim()
              echo "MotioCI DEV/TEST token acquired."
            }
          }
        }
      }
    }

    stage('MotioCI Login (PROD token via API key)') {
      steps {
        withCredentials([file(credentialsId: 'prod-credentials-json', variable: 'APIKEY_FILE')]) {
          container('python') {
            script {
              env.MOTIO_TOKEN_PROD = sh(
                script: '''
                  set -euo pipefail
                  cd MotioCI/api/CLI
                  # pick apiKey for instanceId=1 (PROD)
                  apikey=$(python3 - <<'PY' "$APIKEY_FILE"
import json,sys,os
data=json.load(open(sys.argv[1],"r"))
if isinstance(data, dict): data=[data]
key=next((x.get("apiKey") for x in data if str(x.get("instanceId"))=="1"), "")
print(key,end="")
PY
)
                  [ -n "$apikey" ] || { echo "No PROD apiKey found in prod-credentials-json"; exit 1; }
                  tok=$(python3 ci-cli.py --server="${COGNOS_SERVER_URL}" login --apiKey "$apikey" --instanceId "1" \
                        | awk -F': ' '/Auth Token:/ {print $2}' | tr -d ' \\r\\n')
                  [ -n "$tok" ] || { echo "Empty MotioCI token (PROD)"; exit 1; }
                  echo -n "$tok"
                ''',
                returnStdout: true
              ).trim()
              echo "MotioCI PROD token acquired."
            }
          }
        }
      }
    }

    // ========= Resolve instance IDs by name (once) =========
    stage('Resolve Instance IDs (by name)') {
      steps {
        container('python') {
          script {
            env.DEVTEST_INSTANCE_ID = sh(
              script: '''
                set -euo pipefail
                cd MotioCI/api/CLI
                python3 ci-cli.py --server="${COGNOS_SERVER_URL}" instance ls --xauthtoken="${MOTIO_TOKEN_DEV}" --json \
                | python3 - "${DEVTEST_INSTANCE}" <<'PY'
import sys, json
name = sys.argv[1]
data = json.load(sys.stdin)
print(next((str(x.get('id')) for x in data.get('instances',[]) if x.get('name')==name), ''), end='')
PY
              ''',
              returnStdout: true
            ).trim()

            env.PROD_INSTANCE_ID = sh(
              script: '''
                set -euo pipefail
                cd MotioCI/api/CLI
                python3 ci-cli.py --server="${COGNOS_SERVER_URL}" instance ls --xauthtoken="${MOTIO_TOKEN_PROD}" --json \
                | python3 - "${PROD_INSTANCE}" <<'PY'
import sys, json
name = sys.argv[1]
data = json.load(sys.stdin)
print(next((str(x.get('id')) for x in data.get('instances',[]) if x.get('name')==name), ''), end='')
PY
              ''',
              returnStdout: true
            ).trim()

            if (!env.DEVTEST_INSTANCE_ID || !env.PROD_INSTANCE_ID) {
              error "Could not resolve instance IDs (DEV/TEST='${env.DEVTEST_INSTANCE}', PROD='${env.PROD_INSTANCE}')"
            }
            echo "Resolved IDs: DEV/TEST=${env.DEVTEST_INSTANCE_ID}, PROD=${env.PROD_INSTANCE_ID}"
          }
        }
      }
    }

    // ========= CAM Passport (service account) =========
    // Prefer Jenkins credential 'cognos-service-account' if present; fallback to values you provided.
    stage('Get CAM Passport (TEST)') {
      steps {
        script {
          env.CAM_NAMESPACE_TEST = nsForTarget(env.DEVTEST_INSTANCE)
        }
        container('python') {
          script {
            // try to read Jenkins credential (if defined), else fallback
            def hasCred = false
            try {
              withCredentials([usernamePassword(credentialsId: 'cognos-service-account', usernameVariable: 'U', passwordVariable: 'P')]) {
                hasCred = true
                env.CAM_PASSPORT_TEST = sh(
                  script: '''
                    set -euo pipefail
                    cd MotioCI/api/CLI
                    tmpd=$(mktemp -d)
                    curl -sk -c "$tmpd/cookies.txt" \
                      -d "CAMNamespace=${CAM_NAMESPACE_TEST}&CAMUsername=$U&CAMPassword=$P" \
                      "${COGNOS_SERVER_URL}/p2pd/servlet/dispatch" >/dev/null
                    awk '/cam_passport/ {print $7}' "$tmpd/cookies.txt" | tail -1
                  ''',
                  returnStdout: true
                ).trim()
              }
            } catch (ignored) { hasCred = false }
            if (!hasCred) {
              env.CAM_PASSPORT_TEST = sh(
                script: '''
                  set -euo pipefail
                  cd MotioCI/api/CLI
                  tmpd=$(mktemp -d)
                  curl -sk -c "$tmpd/cookies.txt" \
                    -d "CAMNamespace=${CAM_NAMESPACE_TEST}&CAMUsername=${FALLBACK_COGNOS_USER}&CAMPassword=${FALLBACK_COGNOS_PASS}" \
                    "${COGNOS_SERVER_URL}/p2pd/servlet/dispatch" >/dev/null
                  awk '/cam_passport/ {print $7}' "$tmpd/cookies.txt" | tail -1
                ''',
                returnStdout: true
              ).trim()
            }
            if (!env.CAM_PASSPORT_TEST) { error "Failed to obtain CAM Passport for TEST (namespace=${env.CAM_NAMESPACE_TEST})" }
          }
        }
      }
    }

    stage('Preflight (TEST)') {
      steps {
        container('python') {
          sh '''
            set -euo pipefail
            cd MotioCI/api/CLI
            # DEV/TEST visible
            python3 ci-cli.py --server="${COGNOS_SERVER_URL}" project ls \
              --xauthtoken="${MOTIO_TOKEN_DEV}" --instanceName="${DEVTEST_INSTANCE}" >/dev/null
            # Source label visible by ID in DEV/TEST
            python3 ci-cli.py --server="${COGNOS_SERVER_URL}" label ls \
              --xauthtoken="${MOTIO_TOKEN_DEV}" --instanceName="${DEVTEST_INSTANCE}" --projectName="${PROJECT_NAME}" \
              | grep -q " ${SOURCE_LABEL_ID} " || { echo "PRECHECK FAIL: Label ID ${SOURCE_LABEL_ID} not visible in DEV/TEST"; exit 4; }
            echo "Preflight (TEST) OK"
          '''
        }
      }
    }

    stage('Promote: DEV -> TEST') {
      steps {
        container('python') {
          sh '''
            set -euo pipefail
            cd MotioCI/api/CLI
            echo "Promoting Label ID ${SOURCE_LABEL_ID} from DEV -> TEST as ${TEST_TARGET_LABEL}"
            python3 ci-cli.py --server="${COGNOS_SERVER_URL}" deploy \
              --xauthtoken="${MOTIO_TOKEN_DEV}" \
              --sourceInstanceId="${DEVTEST_INSTANCE_ID}" \
              --targetInstanceId="${DEVTEST_INSTANCE_ID}" \
              --labelId="${SOURCE_LABEL_ID}" \
              --projectName="${PROJECT_NAME}" \
              --targetLabelName="${TEST_TARGET_LABEL}" \
              --camPassportId="${CAM_PASSPORT_TEST}" \
              --namespaceId="${CAM_NAMESPACE_TEST}" 2>deploy_test.err || true
            if grep -qiE "denied|forbidden|401|403" deploy_test.err; then
              echo "TEST ACCESS DENIED:"; cat deploy_test.err; exit 12
            fi
            echo "DEV -> TEST promotion complete."
          '''
        }
      }
    }

    stage('Approve PROD') {
      steps {
        input message: "Promote TEST -> PROD? Project=${env.PROJECT_NAME}, PROD label=${env.PROD_TARGET_LABEL}", ok: "Proceed"
      }
    }

    stage('Get CAM Passport (PROD)') {
      steps {
        script {
          env.CAM_NAMESPACE_PROD = nsForTarget(env.PROD_INSTANCE)
        }
        container('python') {
          script {
            // prefer Jenkins cred; fallback to inline values you provided
            def hasCred = false
            try {
              withCredentials([usernamePassword(credentialsId: 'cognos-service-account', usernameVariable: 'U', passwordVariable: 'P')]) {
                hasCred = true
                env.CAM_PASSPORT_PROD = sh(
                  script: '''
                    set -euo pipefail
                    cd MotioCI/api/CLI
                    tmpd=$(mktemp -d)
                    curl -sk -c "$tmpd/cookies.txt" \
                      -d "CAMNamespace=${CAM_NAMESPACE_PROD}&CAMUsername=$U&CAMPassword=$P" \
                      "${COGNOS_SERVER_URL}/p2pd/servlet/dispatch" >/dev/null
                    awk '/cam_passport/ {print $7}' "$tmpd/cookies.txt" | tail -1
                  ''',
                  returnStdout: true
                ).trim()
              }
            } catch (ignored) { hasCred = false }
            if (!hasCred) {
              env.CAM_PASSPORT_PROD = sh(
                script: '''
                  set -euo pipefail
                  cd MotioCI/api/CLI
                  tmpd=$(mktemp -d)
                  curl -sk -c "$tmpd/cookies.txt" \
                    -d "CAMNamespace=${CAM_NAMESPACE_PROD}&CAMUsername=${FALLBACK_COGNOS_USER}&CAMPassword=${FALLBACK_COGNOS_PASS}" \
                    "${COGNOS_SERVER_URL}/p2pd/servlet/dispatch" >/dev/null
                  awk '/cam_passport/ {print $7}' "$tmpd/cookies.txt" | tail -1
                ''',
                returnStdout: true
              ).trim()
            }
            if (!env.CAM_PASSPORT_PROD) { error "Failed to obtain CAM Passport for PROD (namespace=${env.CAM_NAMESPACE_PROD})" }
          }
        }
      }
    }

    stage('Preflight (PROD)') {
      steps {
        container('python') {
          sh '''
            set -euo pipefail
            cd MotioCI/api/CLI
            python3 ci-cli.py --server="${COGNOS_SERVER_URL}" project ls \
              --xauthtoken="${MOTIO_TOKEN_PROD}" --instanceName="${PROD_INSTANCE}" >/dev/null
            echo "Preflight (PROD) OK"
          '''
        }
      }
    }

    stage('Promote: TEST -> PROD') {
      steps {
        container('python') {
          sh '''
            set -euo pipefail
            cd MotioCI/api/CLI

            # Look up the label we just created in TEST by name to promote to PROD
            TEST_LABEL_ID=$(python3 ci-cli.py --server="${COGNOS_SERVER_URL}" label ls --xauthtoken="${MOTIO_TOKEN_DEV}" \
              --instanceName="${DEVTEST_INSTANCE}" --projectName="${PROJECT_NAME}" --json \
              | python3 - <<'PY'
import sys,json,os
target = os.environ.get("TEST_TARGET_LABEL")
d=json.load(sys.stdin)
print(next((str(x.get('id')) for x in d.get('labels',[]) if x.get('name')==target), ''), end='')
PY
            )
            if [ -z "$TEST_LABEL_ID" ]; then
              echo "Could not find TEST label '${TEST_TARGET_LABEL}' to promote to PROD"; exit 5;
            fi

            echo "Promoting TEST label ID ${TEST_LABEL_ID} -> PROD as ${PROD_TARGET_LABEL}"
            python3 ci-cli.py --server="${COGNOS_SERVER_URL}" deploy \
              --xauthtoken="${MOTIO_TOKEN_PROD}" \
              --sourceInstanceId="${DEVTEST_INSTANCE_ID}" \
              --targetInstanceId="${PROD_INSTANCE_ID}" \
              --labelId="$TEST_LABEL_ID" \
              --projectName="${PROJECT_NAME}" \
              --targetLabelName="${PROD_TARGET_LABEL}" \
              --camPassportId="${CAM_PASSPORT_PROD}" \
              --namespaceId="${CAM_NAMESPACE_PROD}" 2>deploy_prod.err || true

            if grep -qiE "denied|forbidden|401|403" deploy_prod.err; then
              echo "PROD ACCESS DENIED:"; cat deploy_prod.err; exit 12
            fi

            echo "TEST -> PROD promotion complete."
          '''
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
