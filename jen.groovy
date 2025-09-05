stage('Auth: Cognos API session (API key)') {
  steps {
    container('python') {
      withCredentials([string(credentialsId: 'cognos-api-key-prd', variable: 'COGNOS_API_KEY')]) {
        sh '''
          set -eu
          (set -o pipefail) 2>/dev/null || true

          echo "Installing MotioCI CLI dependencies..."
          cd MotioCI/api/CLI
          python3 -m pip install --user -r requirements.txt
          cd - >/dev/null

          echo "Starting Cognos API session (PRD)..."
          BASE="${COGNOS_API_BASE:-https://dhcsprodcognos.ca.analytics.ibm.com/api}/v1"

          rm -f login.json session.json session.redacted.json headers.txt cookies.txt extensions.json || true
          mkdir -p MotioCI/api

          # Step 1: PUT /session with API key
          cat > login.json <<JSON
{ "parameters": [ { "name": "CAMAPILoginKey", "value": "${COGNOS_API_KEY}" } ] }
JSON

          curl --fail-with-body -sS -X PUT "$BASE/session" \
               -H "Content-Type: application/json" \
               -d @login.json \
               -c cookies.txt -b cookies.txt \
               -D headers.txt -o session.json

          SESSION_KEY=$(python3 -c 'import json; print(json.load(open("session.json")).get("session_key",""))')
          if [ -z "$SESSION_KEY" ]; then
            echo "ERROR: No session_key returned from Cognos." >&2
            echo "Response body:"; cat session.json || true
            exit 1
          fi

          # Build IBM-BA-Authorization header safely
          case "$SESSION_KEY" in
            "CAM "*) AUTH_VALUE="$SESSION_KEY" ;;
            "CAM"*)  AUTH_VALUE="$SESSION_KEY" ;;
            *)       AUTH_VALUE="CAM $SESSION_KEY" ;;
          esac

          # Step 2: GET /session WITH AUTH to obtain XSRF-TOKEN cookie
          curl --fail-with-body -sS "$BASE/session" \
               -H "IBM-BA-Authorization: $AUTH_VALUE" \
               -H "Accept: application/json" \
               -c cookies.txt -b cookies.txt \
               -D headers.txt -o /dev/null

          # Extract XSRF token (cookie jar first, then Set-Cookie header)
          XSRF=$(awk '$1 ~ /^#HttpOnly_/ {sub("^#HttpOnly_", "", $1)} $6=="XSRF-TOKEN" {print $7}' cookies.txt | tail -n1 || true)
          if [ -z "${XSRF:-}" ]; then
            XSRF=$(awk 'BEGIN{IGNORECASE=1} /^Set-Cookie:/ && $0 ~ /XSRF-TOKEN/ {match($0,/XSRF-TOKEN=([^;]+)/,m); if (m[1]) print m[1] }' headers.txt | tail -n1 || true)
          fi

          # Step 3: sanity call → /extensions
          # Build headers (POSIX-safe)
          set -- -H "IBM-BA-Authorization: $AUTH_VALUE"
          if [ -n "${XSRF:-}" ]; then
            set -- "$@" -H "X-XSRF-TOKEN: ${XSRF}"
          fi

          curl --fail-with-body -sS "$BASE/extensions" \
               "$@" \
               -c cookies.txt -b cookies.txt \
               -D headers.txt -o extensions.json

          echo "Cognos API session verified."

          # Persist for later stages (do NOT echo values)
          printf "COGNOS_SESSION_KEY=%s\n" "$SESSION_KEY" >  MotioCI/api/motio_env
          printf "COGNOS_XSRF=%s\n"       "${XSRF:-}"     >> MotioCI/api/motio_env
          printf "COGNOS_AUTH_VALUE=%s\n" "$AUTH_VALUE"   >> MotioCI/api/motio_env

          # Redact session.json for safe archiving
          python3 -c 'import json; j=json.load(open("session.json")); j["session_key"]="***redacted***" if "session_key" in j else None; open("session.redacted.json","w").write(json.dumps(j,indent=2))'
        '''
      }
    }
    script {
      def envFile = readFile('MotioCI/api/motio_env').trim()
      envFile.split("\n").each { line -> def (k,v) = line.split('=', 2); env[k] = v }
      echo "Auth stage complete: session_key + XSRF ready."
    }
    archiveArtifacts artifacts: 'login.json,session.redacted.json,headers.txt,cookies.txt,extensions.json', onlyIfSuccessful: false
  }
}
