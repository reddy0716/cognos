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
    // Defaults you can override at build time (kept here so job runs without editing the file)
    COGNOS_SERVER_URL = "https://cgrptmcip01.cloud.cammis.ca.gov"
    PROJECT_NAME      = "Demo"
    SOURCE_LABEL_ID   = "57"                     // <-- change at build if needed
    DEVTEST_INSTANCE  = "Cognos-DEV/TEST"
    PROD_INSTANCE     = "Cognos-PRD"
    TEST_TARGET_LABEL = "TEST-PROMOTED-${BUILD_NUMBER}"
    PROD_TARGET_LABEL = "PROD-PROMOTED-${BUILD_NUMBER}"
    CAM_NAMESPACE_TEST = "AzureAD"               // adjust if DEV/TEST uses different namespace
    CAM_NAMESPACE_PROD = "AzureAD"
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
          echo "Instances: DEV/TEST='${env.DEVTEST_INSTANCE}'  PROD='${env.PROD_INSTANCE}'"
          echo "Source Label ID (DEV): ${env.SOURCE_LABEL_ID}"
        }
      }
    }

    stage('Check Python Availability') {
      steps { 
        container('python') {
          sh '''
            echo "Checking for Python3..."
            which python3 || { echo "Python3 is NOT installed"; exit 1; }
            python3 --version
          '''
        }
      }
    }

    stage('MotioCI Login (Auth Token)') {
      steps {
        withCredentials([
          file(credentialsId: 'prod-credentials-json', variable: 'CREDENTIALS_FILE')
        ]) {
          container('python') {
            script {
              echo "Installing MotioCI CLI dependencies"
              sh '''
                set -euo pipefail
                cd MotioCI/api/CLI
                python3 -m pip install --user -r requirements.txt
                echo "Successfully installed packages"
              '''
              
              echo "Logging into MotioCI with stored credentials file"
              env.MOTIO_AUTH_TOKEN = sh(
                script: '''
                  set -euo pipefail
                  cd MotioCI/api/CLI
                  tok=$(python3 ci-cli.py --server="${COGNOS_SERVER_URL}" login --credentialsFile "$CREDENTIALS_FILE" \
                        | awk -F': ' '/Auth Token:/ {print $2}' | tr -d ' \\r\\n')
                  [ -n "$tok" ] || { echo "ERROR: Empty MotioCI token"; exit 1; }
                  echo -n "$tok"
                ''',
                returnStdout: true
              ).trim()
              echo "MotioCI login completed"
            }
          }
        }
      }
    }

    stage('Resolve Instance IDs (DEV/TEST & PROD)') {
      steps {
        container('python') {
          script {
            env.DEVTEST_INSTANCE_ID = sh(
              script: '''
                set -euo pipefail
                cd MotioCI/api/CLI
                python3 ci-cli.py --server="${COGNOS_SERVER_URL}" instance ls --xauthtoken="${MOTIO_AUTH_TOKEN}" --json \
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
                python3 ci-cli.py --server="${COGNOS_SERVER_URL}" instance ls --xauthtoken="${MOTIO_AUTH_TOKEN}" --json \
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

    stage('Get CAM Passport (TEST)') {
      steps {
        withCredentials([file(credentialsId: 'prod-credentials-json', variable: 'CREDENTIALS_FILE')]) {
          container('python') {
            script {
              env.CAM_PASSPORT_TEST = sh(
                script: '''
                  set -euo pipefail
                  cd MotioCI/api/CLI
                  # Extract user/pass from JSON file credential
                  read USER PASS <<EOF
$(python3 - <<'PY' "$CREDENTIALS_FILE"
import json,sys
p=sys.argv[1]
c=json.load(open(p,'r'))
print(c.get('username',''))
print(c.get('password',''))
PY
)
EOF
                  tmpd=$(mktemp -d)
                  # Login to Cognos (TEST namespace) and capture cam_passport cookie
                  curl -sk -c "$tmpd/cookies.txt" \
                    -d "CAMNamespace=${CAM_NAMESPACE_TEST}&CAMUsername=${USER}&CAMPassword=${PASS}" \
                    "${COGNOS_SERVER_URL}/p2pd/servlet/dispatch" >/dev/null
                  awk '/cam_passport/ {print $7}' "$tmpd/cookies.txt" | tail -1
                ''',
                returnStdout: true
              ).trim()
              if (!env.CAM_PASSPORT_TEST) { error "Failed to obtain CAM Passport for TEST (namespace=${env.CAM_NAMESPACE_TEST})" }
            }
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
            # Target (DEV/TEST) visible
            python3 ci-cli.py --server="${COGNOS_SERVER_URL}" project ls \
              --xauthtoken="${MOTIO_AUTH_TOKEN}" --instanceName="${DEVTEST_INSTANCE}" >/dev/null
            # Source label visible by ID in DEV/TEST
            python3 ci-cli.py --server="${COGNOS_SERVER_URL}" label ls \
              --xauthtoken="${MOTIO_AUTH_TOKEN}" --instanceName="${DEVTEST_INSTANCE}" --projectName="${PROJECT_NAME}" \
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
              --xauthtoken="${MOTIO_AUTH_TOKEN}" \
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

    stage('Get CAM Passport (PROD)') {
      steps {
        withCredentials([file(credentialsId: 'prod-credentials-json', variable: 'CREDENTIALS_FILE')]) {
          container('python') {
            script {
              env.CAM_PASSPORT_PROD = sh(
                script: '''
                  set -euo pipefail
                  cd MotioCI/api/CLI
                  read USER PASS <<EOF
$(python3 - <<'PY' "$CREDENTIALS_FILE"
import json,sys
p=sys.argv[1]
c=json.load(open(p,'r'))
print(c.get('username',''))
print(c.get('password',''))
PY
)
EOF
                  tmpd=$(mktemp -d)
                  curl -sk -c "$tmpd/cookies.txt" \
                    -d "CAMNamespace=${CAM_NAMESPACE_PROD}&CAMUsername=${USER}&CAMPassword=${PASS}" \
                    "${COGNOS_SERVER_URL}/p2pd/servlet/dispatch" >/dev/null
                  awk '/cam_passport/ {print $7}' "$tmpd/cookies.txt" | tail -1
                ''',
                returnStdout: true
              ).trim()
              if (!env.CAM_PASSPORT_PROD) { error "Failed to obtain CAM Passport for PROD (namespace=${env.CAM_NAMESPACE_PROD})" }
            }
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
              --xauthtoken="${MOTIO_AUTH_TOKEN}" --instanceName="${PROD_INSTANCE}" >/dev/null
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

            # Find the label we just created in TEST by name to promote to PROD
            TEST_LABEL_ID=$(python3 ci-cli.py --server="${COGNOS_SERVER_URL}" label ls --xauthtoken="${MOTIO_AUTH_TOKEN}" \
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
              --xauthtoken="${MOTIO_AUTH_TOKEN}" \
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
