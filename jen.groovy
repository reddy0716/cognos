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
        python3 ci-cli.py --server="$COGNOS_SERVER_URL" \
          project ls --xauthtoken="${MOTIO_AUTH_TOKEN}" --instanceName="Cognos-PRD" \
          | tee projects_prd.txt
        if ! grep -i -F -q "${PROJECT_NAME}" projects_prd.txt; then
          echo "ERROR: Project '${PROJECT_NAME}' not found on Cognos-PRD via MotioCI." >&2
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

        echo "Namespaces returned by PRD (name/id):"
        python3 - <<'PY'
import json
d=json.load(open("namespaces_prd.json"))
ns=(((d.get("data") or {}).get("instance") or {}).get("namespaces") or [])
for n in ns:
  print(f"- {n.get('name')} (id={n.get('id')})")
PY

        # Resolve by name OR id (case-insensitive; ignore spaces/symbols)
        mkdir -p MotioCI/api
        python3 - <<'PY'
import json, os, re, sys
d=json.load(open("namespaces_prd.json"))
ns=(((d.get("data") or {}).get("instance") or {}).get("namespaces") or [])
target=os.environ.get("COGNOS_NAMESPACE","AzureAD")
def norm(s): return re.sub(r'[^A-Za-z0-9]', '', (s or '')).lower()
match=None
for n in ns:
    name=(n.get("name") or "")
    nid =(n.get("id") or "")
    if (name.lower()==target.lower() or nid.lower()==target.lower()
        or norm(name)==norm(target) or norm(nid)==norm(target)):
        match=n; break
if not match:
    print(f"ERROR: Namespace '{target}' not found on PRD. Set COGNOS_NAMESPACE to one of:", file=sys.stderr)
    for n in ns:
        print(f"  - {n.get('name')} (id={n.get('id')})", file=sys.stderr)
    sys.exit(1)
name=match.get("name"); nid=match.get("id")
open("MotioCI/api/ns_resolved.env","w").write(f"RESOLVED_NAMESPACE_NAME='{name}'\\nRESOLVED_NAMESPACE_ID='{nid}'\\n")
print(f"Resolved namespace: {name} (id={nid})")
PY

        echo "Pre-deploy health checks passed."
      '''
    }
    // Load resolved namespace into Jenkins env for later stages
    script {
      def nsFile = 'MotioCI/api/ns_resolved.env'
      if (fileExists(nsFile)) {
        def lines = readFile(nsFile).trim().split("\\n")
        lines.each { line ->
          def (k,v) = line.split('=', 2)
          if (v.startsWith("'") && v.endsWith("'") && v.length() >= 2) {
            v = v.substring(1, v.length()-1)
          }
          env[k] = v
        }
        echo "Using namespace: ${env.RESOLVED_NAMESPACE_NAME} (id=${env.RESOLVED_NAMESPACE_ID})"
      } else {
        echo "No resolved namespace file; will use COGNOS_NAMESPACE: ${env.COGNOS_NAMESPACE}"
      }
    }
    archiveArtifacts artifacts: 'extensions.json,headers.txt,MotioCI/api/CLI/projects_prd.txt,namespaces_prd.json,MotioCI/api/ns_resolved.env', onlyIfSuccessful: false
  }
}
