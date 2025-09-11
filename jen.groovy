stage('MotioCI Login') {
  steps {
    withCredentials([file(credentialsId: 'cognos-credentials-json', variable: 'CREDENTIALS_FILE')]) {
      container('python') {
        script {
          def token = sh(
            script: '''
              set -euo pipefail
              cd MotioCI/api/CLI
              python3 ci-cli.py --server="$MOTIO_SERVER" login --credentialsFile "$CREDENTIALS_FILE" \
                > login.out 2>&1 || true

              # Try to extract from various formats
              awk 'match($0,/(Auth[[:space:]]*[Tt]oken|xauthtoken)[[:space:]]*[:=][[:space:]]*([A-Za-z0-9._-]+)/,m){print m[2]}' login.out \
                | tail -n1 > login.token || true
              if [ ! -s login.token ]; then
                awk 'match($0,/"(authToken|xauthtoken)"[[:space:]]*:[[:space:]]*"([^"]+)"/,m){print m[2]}' login.out \
                  | tail -n1 > login.token || true
              fi
              cat login.token
            ''',
            returnStdout: true
          ).trim()

          if (!token) {
            error 'MotioCI login failed: no token parsed (check login.out).'
          }

          env.MOTIO_AUTH_TOKEN = token

          // 🔹 Safe print: show only first + last 6 chars and total length
          def head = token.take(6)
          def tail = token.takeRight(6)
          def masked = (token.length() <= 12) ? ('*' * token.length()) : "${head}...${tail}"
          echo "MotioCI token captured (masked): ${masked}  (len=${token.length()})"
        }
      }
    }
  }
}
