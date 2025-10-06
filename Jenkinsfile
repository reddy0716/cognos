stage('MotioCI Token Debug') {
  steps {
    container('python') {
      sh '''
set -euo pipefail
cd MotioCI/api/CLI
TOKEN=$(cat ../token.txt)
echo "Testing token: ${TOKEN:0:6}... (len=${#TOKEN})"

echo "=== Listing instances ==="
python3 ci-cli.py --server="https://cgrptmcip01.cloud.cammis.ca.gov" \
  instance ls --xauthtoken "$TOKEN" || true

echo "=== Listing projects (DEV/TEST) ==="
python3 ci-cli.py --server="https://cgrptmcip01.cloud.cammis.ca.gov" \
  project ls --instanceName "Cognos-DEV/TEST" --xauthtoken "$TOKEN" || true

echo "=== Listing projects (PRD) ==="
python3 ci-cli.py --server="https://cgrptmcip01.cloud.cammis.ca.gov" \
  project ls --instanceName "Cognos-PRD" --xauthtoken "$TOKEN" || true
      '''
    }
  }
}
