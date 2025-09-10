stage('MotioCI Login (fixed)') {
  steps {
    withCredentials([file(credentialsId: 'cognos-credentials-json', variable: 'CREDENTIALS_FILE')]) {
      dir('MotioCI/api/CLI') {
        // install deps (no interpolation here)
        sh 'python3 -m pip install --user -r requirements.txt'

        script {
          // Run login: capture BOTH stdout+stderr to a file, then parse safely
          def token = sh(
            script: '''
              set -euo pipefail
              : > login.out
              # Login (merge stderr into stdout so we can parse the whole output)
              python3 ci-cli.py --server="$MOTIO_SERVER" login --credentialsFile "$CREDENTIALS_FILE" \
                >login.out 2>&1 || true

              # Extract the token line, then strip the label
              # Accept variants like "Auth Token:" or "Auth token:"
              grep -iE '^Auth[[:space:]]+Token:' login.out | sed -e 's/^[^:]*:[[:space:]]*//' > login.token || true
              cat login.token
            ''',
            returnStdout: true
          ).trim()

          if (!token) {
            // Show non-sensitive diagnostics if parsing failed
            sh '''
              set -e
              echo "------ MotioCI login output (sanitized) ------"
              sed 's/"password":[^,}]*/"password":"***"/g' login.out || true
              echo "------------------------------------------------"
            '''
            error 'MotioCI login failed: token not returned (check output above).'
          }

          env.MOTIO_AUTH_TOKEN = token
          echo "MotioCI login OK (token length: ${env.MOTIO_AUTH_TOKEN.length()})"
        }
      }
    }
  }
}
