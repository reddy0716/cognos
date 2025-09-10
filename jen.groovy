stage('Login to Cognos API') {
  steps {
    script {
      withCredentials([usernamePassword(
        credentialsId: 'Cognos_authentication',
        usernameVariable: 'COGNOS_USERNAME',
        passwordVariable: 'COGNOS_PASSWORD'
      )]) {
        sh '''
          set -euo pipefail

          # Files for response/body/status
          RESP_BODY="response.json"
          RESP_CODE="response.code"

          # Login request with robust curl options
          curl --silent --show-error --fail-with-body \
               --connect-timeout 10 --max-time 60 \
               --retry 3 --retry-connrefused --retry-delay 2 \
               -X PUT \
               -H "Content-Type: application/json" \
               -d "$(cat <<JSON
          {
            "username": "'"${COGNOS_USERNAME}"'",
            "password": "'"${COGNOS_PASSWORD}"'"
          }
JSON
          )" \
          "${COGNOS_API_URL}/session" \
          -o "$RESP_BODY" -w "%{http_code}" > "$RESP_CODE"

          # Basic HTTP code check
          code=$(cat "$RESP_CODE" || true)
          if [ "$code" -lt 200 ] || [ "$code" -ge 300 ]; then
            echo "Login failed. HTTP $code"
            echo "Response body:"
            cat "$RESP_BODY" || true
            exit 1
          fi
        '''

        // Parse JSON in Jenkins and extract token safely
        def json = readJSON file: 'response.json'
        if (!json?.authToken) {
          error "Failed to authenticate: 'authToken' missing in response"
        }

        // Store the token in env without printing it
        env.COGNOS_AUTH_TOKEN = json.authToken.toString()
        echo 'Successfully authenticated to Cognos API.'
      }
    }
  }
}
