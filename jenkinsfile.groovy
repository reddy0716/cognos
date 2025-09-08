stage('MotioCI login → token') {
  steps {
    container('python') {
      sh '''
        set -euo pipefail
        cd MotioCI/api/CLI
        TOKEN="$(python3 ci-cli.py --server=https://cgrptmcip01.cloud.cammis.ca.gov \
          --non-interactive login --credentialsFile ${CRED_FILE} | tr -d '\\r\\n')"
        test -n "$TOKEN" || (echo "Empty token" >&2; exit 1)
        printf "%s" "$TOKEN" > .motio_token
      '''
    }
  }
}

stage('List instances') {
  steps {
    container('python') {
      sh '''
        set -euo pipefail
        cd MotioCI/api/CLI
        python3 ci-cli.py --server=https://cgrptmcip01.cloud.cammis.ca.gov \
          instance ls --xauthtoken "$(cat .motio_token)"
      '''
    }
  }
}
