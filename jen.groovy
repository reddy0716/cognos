stage('MotioCI Login (safe)') {
  steps {
    withCredentials([file(credentialsId: 'cognos-credentials-json', variable: 'CREDENTIALS_FILE')]) {
      dir('MotioCI/api/CLI') {
        // install once
        sh 'python3 -m pip install --user -r requirements.txt'

        script {
          // No Groovy interpolation; shell expands $MOTIO_SERVER and $CREDENTIALS_FILE
          def raw = sh(
            script: '''
              set -euo pipefail
              python3 ci-cli.py --server="$MOTIO_SERVER" login --credentialsFile "$CREDENTIALS_FILE" \
                | awk -F': ' '/^Auth Token/{print $2}'
            ''',
            returnStdout: true
          ).trim()

          if (!raw) {
            // Helpful diag without leaking secrets
            sh '''
              set -e
              echo "DEBUG: printing login help for diagnostics:"
              python3 ci-cli.py --help || true
              python3 ci-cli.py login --help || true
            '''
            error 'MotioCI login failed: token not returned'
          }

          env.MOTIO_AUTH_TOKEN = raw
          echo "MotioCI login OK (token length: ${env.MOTIO_AUTH_TOKEN.length()})"
        }
      }
    }
  }
}
