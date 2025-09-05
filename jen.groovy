stage('Source checks (DEV/TEST)') {
  steps {
    container('python') {
      sh '''
        set -eu
        (set -o pipefail) 2>/dev/null || true

        cd MotioCI/api/CLI

        echo "1) Verify DEV/TEST instance & project via GraphQL (robust)"
        cat > ../src_projects.gql.json <<JSON
{"query":"query($id: Long!){ instance(id:$id){ id name projects { edges { node { id name description }}}}}","variables":{"id":${SOURCE_INSTANCE_ID}}}
JSON

        curl -sS --fail-with-body -X POST "${COGNOS_SERVER_URL}/api/graphql" \
          -H "Content-Type: application/json" \
          -H "x-auth-token: ${MOTIO_AUTH_TOKEN}" \
          -d @../src_projects.gql.json \
          -D ../src_projects.headers.txt -o ../src_projects.json

        python3 - <<'PY'
import json, os, sys
d=json.load(open("..//src_projects.json"))
if d.get("errors"):
    print("GraphQL errors:", d["errors"], file=sys.stderr); sys.exit(2)
inst=(d.get("data") or {}).get("instance")
if not inst:
    print("No instance returned for given id.", file=sys.stderr); sys.exit(2)
print(f"Instance OK: id={inst.get('id')} name={inst.get('name')}")
projects=[(e.get('node') or {}) for e in (inst.get('projects',{}).get('edges') or [])]
names=[p.get('name') for p in projects]
print("Projects on source:", names)
target=os.environ.get("PROJECT_NAME","Demo").lower()
ok=any((n or "").lower()==target for n in names)
if not ok:
    print(f"ERROR: Project '{os.environ.get('PROJECT_NAME')}' not found on source instance.", file=sys.stderr)
    sys.exit(3)
PY

        echo "2) Verify label on DEV/TEST via CLI (tolerant; archives output)"
        set +e
        python3 ci-cli.py --server="${COGNOS_SERVER_URL}" \
          label ls --xauthtoken="${MOTIO_AUTH_TOKEN}" \
          --instanceName="Cognos-DEV/TEST" --projectName="${PROJECT_NAME}" \
          | tee ../labels_src.txt
        CLI_RC=${PIPESTATUS[0]:-1}
        set -e

        # Look for LABEL_ID regardless of CLI RC (some builds return non-JSON/200)
        if ! grep -E -q "(^|[^0-9])${LABEL_ID}([^0-9]|$)" ../labels_src.txt; then
          echo "ERROR: Label id '${LABEL_ID}' not found on DEV/TEST project '${PROJECT_NAME}'." >&2
          echo "Hint: If CLI failed (rc=${CLI_RC}), your MotioCI token may lack access to DEV/TEST."
          sed -n '1,200p' ../labels_src.txt
          exit 1
        fi

        echo "Source DEV/TEST checks OK."
      '''
    }
    archiveArtifacts artifacts: 'MotioCI/api/src_projects.json,MotioCI/api/src_projects.headers.txt,MotioCI/api/labels_src.txt', onlyIfSuccessful: false
  }
}
