stage('MotioCI Auth (CAMPassport exchange)') {
  steps {
    container('python') {
      sh '''
        set -eu
        (set -o pipefail) 2>/dev/null || true

        # ⚠ TEST ONLY: Hardcode a fresh PRD service-account CAMPassport
        CAMPASSPORT='REPLACE_WITH_FRESH_SERVICE_ACCOUNT_CAM_PASSPORT'
        NS_ID='AzureAD'   # PRD namespace id (display name is "Azure")

        mkdir -p MotioCI/api/CLI
        cd MotioCI/api/CLI

        echo "Attempting MotioCI login with CAMPassport (CLI)..."
        # Try the CLI login path that some MotioCI builds support
        set +e
        LOGIN_OUT=$(python3 ci-cli.py --server="${COGNOS_SERVER_URL}" \
          login --camPassportId "${CAMPASSPORT}" --namespaceId "${NS_ID}" 2>&1)
        RC=$?
        set -e

        TOKEN_CLEAN=''
        if [ $RC -eq 0 ]; then
          # Extract a UUID token (8-4-4-4-12), regardless of surrounding text
          TOKEN_CLEAN=$(printf "%s\\n" "$LOGIN_OUT" | grep -oiE '[0-9a-f]{8}-([0-9a-f]{4}-){3}[0-9a-f]{12}' | tail -n1 || true)
        fi

        if [ -z "${TOKEN_CLEAN}" ]; then
          echo "ERROR: MotioCI CLI did not return a valid token with CAMPassport."
          echo "Output tail for admins:"
          printf "%s\\n" "$LOGIN_OUT" | tail -n 50
          exit 1
        fi

        # Persist token for later stages (don’t echo the value)
        cd ..
        printf "MOTIO_AUTH_TOKEN=%s\\n" "$TOKEN_CLEAN" > motio_token.env
        cd - >/dev/null

        # Quick sanity ping so we fail fast if token is unusable
        curl -sS --fail-with-body -X POST "${COGNOS_SERVER_URL}/api/graphql" \
          -H "Content-Type: application/json" \
          -H "x-auth-token: ${TOKEN_CLEAN}" \
          -d '{"query":"{ __typename }"}' >/dev/null

        echo "MotioCI token set via CAMPassport."
      '''
    }
    // Load the token into Jenkins env for subsequent stages
    script {
      def t = readFile('MotioCI/api/motio_token.env').trim()
      t.split("\n").each { line -> def (k,v) = line.split('=',2); env[k]=v }
      echo "MOTIO_AUTH_TOKEN is ready for prechecks and deploy."
    }
  }
}
