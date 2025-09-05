stage('Auth: Cognos API session (API key)') {
  steps {
    container('python') {
      withCredentials([
        string(credentialsId: 'cognos-api-key-prd', variable: 'COGNOS_API_KEY')
      ]) {
        sh '''
          set -euo pipefail

          echo "Installing MotioCI CLI dependencies..."
          cd MotioCI/api/CLI
          python3 -m pip install --user -r requirements.txt
          cd - >/dev/null

          echo "Starting Cognos API session (PRD)..."
          BASE="${COGNOS_API_BASE:-https://dhcsprodcognos.ca.analytics.ibm.com/api}/v1"

          rm -f login.json session.json session.redacted.json headers.txt cookies.txt extensions.json || true
          mkdir -p MotioCI/api

          # Build API-key payload
          cat > login.json <<JSON
{ "parameters": [ { "name": "CAMAPILoginKey", "value": "${COGNOS_API_KEY}" } ] }
JSON

          # Step 1: PUT /session -> expect session_key in JSON
          curl --fail-with-body -sS -X PUT "$BASE/session" \
               -H "Content-Type: application/json" \
               -d @login.json \
               -c cookies.txt -b cookies.txt \
               -D headers.txt -o session.json

          # Extract session_key (do NOT print it)
          SESSION_KEY=$(python3 - <<'PY'
import json,sys
try:
  j=json.load(open("session.json"))
  print(j.get("session_key",""))
except Exception:
  print("")
PY
)
          if [ -z "$SESSION_KEY" ]; then
            echo "ERROR: No session_key returned from Cognos." >&2
            echo "Response body:"
            cat session.json || true
            exit 1
          fi

          # Step 2: GET /session to obtain XSRF-TOKEN cookie
          curl --fail-with-body -sS "$BASE/session" \
               -c cookies.txt -b cookies.txt \
               -D headers.txt -o /dev/null

          # Extract XSRF token (may or may not be present)
          XSRF=$(awk '$1 ~ /^#HttpOnly_/ {sub("^#HttpOnly_", "", $1)} $6=="XSRF-TOKEN" {print $7}' cookies.txt | tail -n1 || true)

          # Build IBM-BA-Authorization header safely (some tenants return "CAM <key>", others just "<key>")
          case "$SESSION_KEY" in
            "CAM "*) AUTH_VALUE="$SESSION_KEY" ;;
            "CAM"*)  AUTH_VALUE="$SESSION_KEY" ;;
            *)       AUTH_VALUE="CAM $SESSION_KEY" ;;
          esac

          # Step 3: sanity call (noisy endpoint, harmless)
          CURL_HEADERS=(-H "IBM-BA-Authorization: $AUTH_VALUE")
          if [ -n "${XSRF:-}" ]; then
            CURL_HEADERS+=( -H "X-XSRF-TOKEN: ${XSRF}" )
          fi

          curl --fail-with-body -sS "$BASE/extensions" \
               "${CURL_HEADERS[@]}" \
               -c cookies.txt -b cookies.txt \
               -D headers.txt -o extensions.json

          echo "Cognos API session verified."

          # Persist secrets for later stages (do NOT echo values)
          printf "COGNOS_SESSION_KEY=%s\n" "$SESSION_KEY" >  MotioCI/api/motio_env
          printf "COGNOS_XSRF=%s\n"       "${XSRF:-}"     >> MotioCI/api/motio_env
          printf "COGNOS_AUTH_VALUE=%s\n" "$AUTH_VALUE"   >> MotioCI/api/motio_env

          # Redact session.json for safe archiving
          python3 - <<'PY'
import json,sys
j=json.load(open("session.json"))
if "session_key" in j: j["session_key"]="***redacted***"
open("session.redacted.json","w").write(json.dumps(j,indent=2))
PY
        '''
      }
    }
  }
}
