stage('Pre-deploy health checks (Cognos REST + MotioCI)') {
  steps {
    container('python') {
      sh '''
        set -eu
        (set -o pipefail) 2>/dev/null || true

        # Require auth artifacts from prior stages
        [ -f MotioCI/api/motio_env ] || { echo "Missing MotioCI/api/motio_env (run Auth + MotioCI login first)"; exit 1; }
        . MotioCI/api/motio_env

        BASE="${COGNOS_API_BASE:-https://dhcsprodcognos.ca.analytics.ibm.com/api}/v1"

        echo "1) Cognos REST check: /extensions with session_key"
        # Build headers POSIX-safely
        set -- -H "IBM-BA-Authorization: ${COGNOS_AUTH_VALUE}" -H "Accept: application/json"
        if [ -n "${COGNOS_XSRF:-}" ]; then
          set -- "$@" -H "X-XSRF-TOKEN: ${COGNOS_XSRF}"
        fi

        # Call /extensions (should be 200 + non-empty JSON)
        curl -sS --fail-with-body "$BASE/extensions" \
             "$@" \
             -c cookies.txt -b cookies.txt \
             -D headers.txt -o extensions.json

        EXT_OK=$(python3 - <<'PY'
import json,sys
try:
  data=json.load(open("extensions.json"))
  ok = isinstance(data,(dict,list)) and len(data)>0
  print(1 if ok else 0)
except Exception:
  print(0)
PY
)
        if [ "$EXT_OK" -ne 1 ]; then
          echo "ERROR: /extensions returned empty or invalid JSON." >&2
          exit 1
        fi
        echo "Cognos REST OK."

        echo "2) MotioCI project presence on PRD"
        cd MotioCI/api/CLI

        # List projects for PRD; write in current dir and read the same file
        python3 ci-cli.py --server="${MOTIO_SERVER}" \
          project ls --xauthtoken="${TOKEN}" --instanceName="Cognos-PRD" \
          > projects_prd.json

        PROJ_OK=$(python3 - <<'PY'
import json,os
try:
  data=json.load(open("projects_prd.json"))
  target=os.environ.get("PROJECT_NAME","Demo")
  # handle either list or dict-with-items
  items = data.get("items") if isinstance(data,dict) else data
  if items is None: items = data.get("projects") if isinstance(data,dict) else []
  found = False
  for it in (items or []):
    if isinstance(it,dict) and it.get("name")==target:
      found=True; break
  print(1 if found else 0)
except Exception:
  print(0)
PY
)
        if [ "$PROJ_OK" -ne 1 ]; then
          echo "ERROR: Project '${PROJECT_NAME}' not found on Cognos-PRD via MotioCI." >&2
          exit 1
        fi
        echo "MotioCI project OK."

        echo "3) MotioCI namespace check on PRD (expects '${NAMESPACE_ID:-AzureAD}')"
        cd - >/dev/null

        curl -sS --fail-with-body -X POST "${MOTIO_SERVER}/api/graphql" \
          -H "Content-Type: application/json" \
          -H "x-auth-token: ${TOKEN}" \
          -d '{"query":"query($id: Long!){ instance(id:$id){ namespaces { id name } } }", "variables":{"id":'"${TGT_INSTANCE_ID:-1}"'}}' \
          > namespaces_prd.json

        NS_OK=$(python3 - <<'PY'
import json,os
try:
  d=json.load(open("namespaces_prd.json"))
  target=os.environ.get("NAMESPACE_ID","AzureAD")
  ns = (((d.get("data") or {}).get("instance") or {}).get("namespaces") or [])
  print(1 if any(isinstance(n,dict) and n.get("name")==target for n in ns) else 0)
except Exception:
  print(0)
PY
)
        if [ "$NS_OK" -ne 1 ]; then
          echo "ERROR: Namespace '${NAMESPACE_ID}' not found on PRD (MotioCI GraphQL)." >&2
          exit 1
        fi
        echo "Namespace OK."

        echo "Pre-deploy health checks passed."
      '''
    }
    // Save useful artifacts for support (no secrets)
    archiveArtifacts artifacts: 'extensions.json,headers.txt,MotioCI/api/CLI/projects_prd.json,namespaces_prd.json', onlyIfSuccessful: false
  }
}
