stage('MotioCI Login') {
      steps {
        withCredentials([file(credentialsId: 'cognos-credentials-json', variable: 'CREDENTIALS_FILE')]) {
          dir('MotioCI/api/CLI') {
            // install requirements first time
            sh 'python3 -m pip install --user -r requirements.txt'
            script {
              // Run login using the JSON file
              env.MOTIO_AUTH_TOKEN = sh(
                script: """
                  python3 ci-cli.py --server="${MOTIO_SERVER}" login --credentialsFile "$CREDENTIALS_FILE" \
                  | grep "Auth Token:" | cut -d: -f2 | tr -d ' '
                """,
                returnStdout: true
              ).trim()

              if (!env.MOTIO_AUTH_TOKEN) {
                error 'MotioCI login failed: token not returned'
              }

              echo "MotioCI login successful (token length: ${env.MOTIO_AUTH_TOKEN.length()})"
            }
          }
        }
      }
    }
