pipeline {
  agent {
    kubernetes {
      yaml """
apiVersion: v1
kind: Pod
spec:
  serviceAccountName: jenkins
  containers:
    - name: python
      image: 136299550619.dkr.ecr.us-west-2.amazonaws.com/cammisboto3:1.2.0
      tty: true
      command: ["/bin/bash"]
      workingDir: /home/jenkins/agent
"""
    }
  }

  environment {
    MOTIO_SERVER = "https://cgrptmcip01.cloud.cammis.ca.gov"      // MotioCI URL
    PROJECT_NAME = "Demo"
    SRC_INSTANCE_ID = "3"                                         // DEV/TEST
    TGT_INSTANCE_ID = "1"                                         // PROD
    NAMESPACE_ID = "azure"
    USERNAME = "cmarks"
    DEV_APIKEY = "DEVTEST_API_KEY_HERE"                           // replace with real
    PRD_APIKEY = "PRD_API_KEY_HERE"                               // replace with real
  }

  options {
    timestamps()
    disableConcurrentBuilds()
  }

  stages {

    stage('Install CLI dependencies') {
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
        container('python') {
          script {
            sh '''
              set -euo pipefail
              cd MotioCI/api/CLI

              echo "Creating creds.json with both DEV/TEST and PRD credentials..."
              cat > creds.json <<ENDJSON
              [
                {
                  "instanceId": "${TGT_INSTANCE_ID}",
                  "namespaceId": "${NAMESPACE_ID}",
                  "username": "${USERNAME}",
                  "camPassportId": "${PRD_APIKEY}"
                },
                {
                  "instanceId": "${SRC_INSTANCE_ID}",
                  "namespaceId": "${NAMESPACE_ID}",
                  "username": "${USERNAME}",
                  "camPassportId": "${DEV_APIKEY}"
                }
              ]
              ENDJSON

              python3 ci-cli.py --server="${MOTIO_SERVER}" login --credentialsFile creds.json > login.out 2>&1 || true

              # Extract token
              awk 'match($0,/(Auth[[:space:]]*[Tt]oken|xauthtoken)[:=][[:space:]]*([A-Za-z0-9._-]+)/,m){print m[2]}' login.out | tail -n1 > login.token || true
              if [ ! -s login.token ]; then
                awk 'match($0,/"(authToken|xauthtoken)"[[:space:]]*:[[:space:]]*"([^"]+)"/,m){print m[2]}' login.out | tail -n1 > login.token || true
              fi
              TOKEN=$(cat login.token || true)
              if [ -z "$TOKEN" ]; then
                echo "Login failed. Check login.out below:"
                sed -E 's/"camPassportId":[^,}]*/"camPassportId":"***"/g' login.out | sed -n '1,120p'
                exit 1
              fi
              echo "MotioCI token captured (len=${#TOKEN})"
              echo "$TOKEN" > ../token.txt
            '''
          }
        }
      }
    }

    stage('Deploy DEV/TEST → PRD') {
      steps {
        container('python') {
          sh '''
            set -euo pipefail
            cd MotioCI/api/CLI
            TOKEN=$(cat ../token.txt)

            echo "=== Running deploy for project: ${PROJECT_NAME} ==="
            python3 ci-cli.py --server="${MOTIO_SERVER}" deploy \
              --xauthtoken "$TOKEN" \
              --sourceInstanceId ${SRC_INSTANCE_ID} \
              --targetInstanceId ${TGT_INSTANCE_ID} \
              --projectName "${PROJECT_NAME}" \
              --labelId 57 \
              --targetLabelName "PROMOTED-${BUILD_NUMBER}" \
              --namespaceId "${NAMESPACE_ID}" \
              --username "${USERNAME}" \
              --camPassportId "${PRD_APIKEY}" > deploy.out 2>&1 || true

            echo "=== Deploy output ==="
            sed -E 's/"camPassportId":[^,}]*/"camPassportId":"***"/g' deploy.out | sed -n '1,160p'
          '''
        }
      }
    }
  }

  post {
    failure {
      echo "Deployment failed. Check logs above."
    }
  }
}
