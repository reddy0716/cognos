
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
        - name: REQUESTS_CA_BUNDLE
          value: "/etc/pki/tls/certs/ca-bundle.crt"
        - name: SSL_CERT_FILE
          value: "/etc/pki/tls/certs/ca-bundle.crt"
        - name: CI
          value: "1"
      volumeMounts:
        - name: jenkins-trusted-ca-bundle
          mountPath: /etc/pki/tls/certs
"""
    }
  }

  // Defaults match your current setup; you can override later via parameters if needed.
  environment {
    GIT_BRANCH   = "${BRANCH_NAME}"
    MOTIO_SERVER = "https://cgrptmcip01.cloud.cammis.ca.gov"

    // Source/Target config
    SRC_INSTANCE_ID   = "3"     // DEV/TEST
    TGT_INSTANCE_ID   = "1"     // PRD
    PROJECT_NAME      = "Demo"
    SOURCE_LABEL_ID   = "57"
    TARGET_LABEL      = "PROMOTED-20250712-115"
    NAMESPACE_ID      = "AzureAD"
  }

  options {
    disableConcurrentBuilds()
    timestamps()
  }

  stages {
    stage('Initialize') {
      steps {
        echo "Branch: ${env.GIT_BRANCH}"
        echo "Initializing MotioCI Cognos deployment pipeline..."
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
            set -euo pipefail
            cd MotioCI/api/CLI
            python3 -m pip install --user -r requirements.txt
            echo "Dependencies installed."
          '''
        }
      }
    }

    stage('MotioCI Login (Token)') {
      steps {
        withCredentials([file(credentialsId: 'prod-credentials-json', variable: 'CREDENTIALS_FILE')]) {
          container('python') {
            sh '''
              set -euo pipefail
              cd MotioCI/api/CLI

              # Get auth token (ci-cli prints token only on success)
              TOKEN=$(python3 ci-cli.py --server="${MOTIO_SERVER}" --non-interactive login --credentialsFile "$CREDENTIALS_FILE" | tail -n1 | tr -d '\\r')
              [ -n "$TOKEN" ] || { echo "ERROR: Empty MotioCI token"; exit 1; }

              # Stash for later stages
              printf "TOKEN=%s\n" "$TOKEN" > ../motio_env
            '''
          }
          script {
            def envFile = readFile('MotioCI/api/motio_env').trim()
            envFile.split("\n").each { line ->
              def (k,v) = line.split('=', 2)
              env[k] = v
            }
            echo "MotioCI token captured."
          }
        }
      }
    }

    stage('Debug Namespaces (PRD)') {
      steps {
        container('python') {
          sh '''
            set -euo pipefail
            BODY=$(cat <<JSON
{"query":"query($id: Long!){ instance(id:$id){ namespaces { id name } } }","variables":{"id":'"${TGT_INSTANCE_ID}"'}}
JSON
)
            curl --fail-with-body -sS -X POST "${MOTIO_SERVER}/api/graphql" \
              -H "Content-Type: application/json" \
              -H "x-auth-token: ${TOKEN}" \
              --cacert /etc/pki/tls/certs/ca-bundle.crt \
              -d "$BODY" \
            | python3 -m json.tool | tee namespaces_target.json
          '''
        }
      }
    }

    stage('Get CAM Passport (service account)') {
      steps {
        withCredentials([usernamePassword(credentialsId: 'Cognosserviceaccount', usernameVariable: 'COG_USER', passwordVariable: 'COG_PASS')]) {
          container('python') {
            sh '''
              set -euo pipefail

              # Example login payload: adjust if your Cognos auth endpoint uses different keys/shape.
              LOGIN_PAYLOAD=$(cat <<JSON
{
  "parameters": {
    "CAMNamespace": "${NAMESPACE_ID}",
    "CAMUsername": "${COG_USER}",
    "CAMPassword": "${COG_PASS}"
  }
}
JSON
)

              # Attempt to fetch camPassportId from Cognos via MotioCI gateway/API.
              CAM=$(curl --fail-with-body -sS -X POST "${MOTIO_SERVER}/api/cognos/auth/login" \
                       -H "Content-Type: application/json" \
                       --cacert /etc/pki/tls/certs/ca-bundle.crt \
                       -d "$LOGIN_PAYLOAD" \
                | python3 -c "import sys, json; print(json.load(sys.stdin).get('camPassportId',''))" )

              [ -n "$CAM" ] || { echo "ERROR: camPassportId was not returned. Verify auth endpoint & creds."; exit 1; }

              printf "CAMPASSPORT=%s\n" "$CAM" >> MotioCI/api/motio_env
            '''
          }
          script {
            def envFile = readFile('MotioCI/api/motio_env').trim()
            envFile.split("\n").each { line ->
              def (k,v) = line.split('=', 2)
              env[k] = v
            }
            echo "camPassportId obtained."
          }
        }
      }
    }

    stage('Deploy (DEV/TEST → PRD)') {
      steps {
        container('python') {
          sh '''
            set -euo pipefail
            cd MotioCI/api/CLI

            echo "Sanity: list projects on target instance..."
            python3 ci-cli.py --server="${MOTIO_SERVER}" project ls --xauthtoken="${TOKEN}" --instanceId="${TGT_INSTANCE_ID}" \
              | tee ../../projects_target.json

            echo "Deploying label ${SOURCE_LABEL_ID} from instance ${SRC_INSTANCE_ID} (project ${PROJECT_NAME}) to instance ${TGT_INSTANCE_ID} with target label ${TARGET_LABEL}..."
            python3 ci-cli.py --server="${MOTIO_SERVER}" \
              --non-interactive deploy \
              --xauthtoken="${TOKEN}" \
              --sourceInstanceId="${SRC_INSTANCE_ID}" \
              --targetInstanceId="${TGT_INSTANCE_ID}" \
              --labelId="${SOURCE_LABEL_ID}" \
              --projectName="${PROJECT_NAME}" \
              --targetLabelName="${TARGET_LABEL}" \
              --camPassportId="${CAMPASSPORT}" \
              --namespaceId="${NAMESPACE_ID}" \
            | tee ../../deploy_output.json

            echo "Verification: labels after deployment (target instance)"
            python3 ci-cli.py --server="${MOTIO_SERVER}" \
              label ls --xauthtoken="${TOKEN}" --instanceId="${TGT_INSTANCE_ID}" --projectName="${PROJECT_NAME}" \
            | tee ../../verify_labels.json
          '''
        }
      }
    }
  }

  post {
    always {
      // Keep evidence even on failure
      archiveArtifacts artifacts: 'namespaces_target.json, MotioCI/deploy_output.json, MotioCI/verify_labels.json, projects_target.json', onlyIfSuccessful: false
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
