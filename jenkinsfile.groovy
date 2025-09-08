stage('MotioCI login → token') {
  steps {
    container('python') {
      sh '''
        set -euo pipefail
        cd MotioCI/api/CLI

        CREDS_JSON=$(cat <<'JSON'
[
  {
    "password": {
      "namespaceId": "'"${NAMESPACE_ID}"'",
      "username": "'"${COGNOS_USER}"'",
      "password": "'"${COGNOS_PASS}"'"
    }
  }
]
JSON
)

        TOKEN="$(python3 ci-cli.py --server="${MOTIO_SERVER}" --non-interactive login --credentials "${CREDS_JSON}")"
        test -n "${TOKEN}" || (echo "Empty token from MotioCI login" >&2; exit 1)
        printf "%s" "${TOKEN}" > .motio_token
      '''
    }
  }
}
