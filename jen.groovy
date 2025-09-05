stage('Pre-deploy health checks (Cognos REST + MotioCI)') {
  steps {
    container('python') {
      sh '''
        set -eu
        (set -o pipefail) 2>/dev/null || true

        # Require auth artifacts from prior stages
        [ -f MotioCI/api/motio_env ] || { echo "Missing MotioCI/api/motio_env (run Auth + MotioCI login first)"; exit 1; }
        . MotioCI/api/motio_env

        BASE="${COGNOS_API_BASE}/v1"

        echo "1) Cognos REST check: /extensions with session_key"
        set -- -H "IBM-BA-Authorization: ${COGNOS_AUTH_VALUE}" -H "Accept: application/json"
        if [ -n "${COGNOS_XSRF:-}" ]; then
          set -- "$@" -H "X-XSRF-TOKEN: ${COGNOS_XSRF}"
        fi

        curl -sS --fail-with-body "$BASE/extensions" \
             "$@" \
             -c cookies.txt -b cookies.txt \
             -D headers.txt -o extensions.json

        python3 - <<'PY'
import json,sys
try:
  data=json.load(open("extensions.json"))
  assert (isinstance(data,(dict,list)) and len(data)>0)
  print("Cognos REST OK.")
except Exception as e:
  print("ERROR: /extensions invalid/empty JSON", e, file=sys.stderr); sys.exit(1)
PY

        echo "2) MotioCI project presence on PRD"
        cd MotioCI/api/CLI

        # Get the project list as raw text and archive it
        python3 ci-cli.py --server="$COGNOS_SERVER_URL" \
          project ls --xauthtoken="${MOTIO_AUTH_TOKEN}" --instanceName="Cognos-PRD" \
          | tee projects_prd.txt

        # Text-safe check for ${PROJECT_NAME} (case-insensitive)
        if ! grep -i -F -q "${PROJECT_NAME}" projects_prd.txt; then
          echo "ERROR: Project '${PROJECT_NAME}' not found on Cognos-PRD via MotioCI." >&2
          echo "Available projects (first 200 lines):"
          sed -n '1,200p' projects_prd.txt
          exit 1
        fi
        echo "MotioCI project OK."
        cd - >/dev/null

        echo "3) MotioCI namespace check on PRD (expects '${COGNOS_NAMESPACE}')"
        curl -sS --fail-with-body -X POST "${COGNOS_SERVER_URL}/api/graphql" \
          -H "Content-Type: application/json" \
          -H "x-auth-token: ${MOTIO_AUTH_TOKEN}" \
          -d '{"query":"query($id: Long!){ instance(id:$id){ namespaces { id name } } }", "variables":{"id":'"${TARGET_INSTANCE_ID:-1}"'}}' \
          > namespaces_prd.json

        python3 - <<'PY'
import json,os,sys
target=os.environ.get("COGNOS_NAMESPACE","AzureAD")
try:
  d=json.load(open("namespaces_prd.json"))
  ns = (((d.get("data") or {}).get("instance") or {}).get("namespaces") or [])
  ok = any(isinstance(n,dict) and n.get("name")==target for n in ns)
  if not ok:
    print(f"ERROR: Namespace '{target}' not found on PRD (MotioCI GraphQL).", file=sys.stderr); sys.exit(1)
  print("Namespace OK.")
except Exception as e:
  print("ERROR reading namespaces_prd.json:", e, file=sys.stderr); sys.exit(1)
PY

        echo "Pre-deploy health checks passed."
      '''
    }
    // Save useful artifacts for support (no secrets)
    archiveArtifacts artifacts: 'extensions.json,headers.txt,MotioCI/api/CLI/projects_prd.txt,namespaces_prd.json', onlyIfSuccessful: false
  }
}
