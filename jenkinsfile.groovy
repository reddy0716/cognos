stage('MotioCI login → token') {
  steps {
    container('python') {
      sh '''
        set -euo pipefail
        cd MotioCI/api/CLI
        CREDS_JSON=$(cat <<'JSON'
[
  {
    "type": "password",
    "username": "'"${COGNOS_USER}"'",
    "password": "'"${COGNOS_PASS}"'",
    "namespaceId": "azure"
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
