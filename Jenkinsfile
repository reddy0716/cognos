stage('MotioCI Deploy (DEV → PRD)') {
  steps {
    container('python') {
      script {
        sh '''
set -euo pipefail
cd MotioCI/api/CLI

# Read token captured during login
TOKEN=$(cat ../token.txt)

# Use label ID from DEV/TEST that you want to promote
# (for example, 82 from your earlier test)
SOURCE_LABEL_ID=82

echo "=== Starting Promotion from DEV/TEST → PRD ==="
echo "Using token: ${TOKEN:0:6}... (len=${#TOKEN})"
echo "Promoting Label ID: $SOURCE_LABEL_ID from DEV/TEST to PRD"

python3 ci-cli.py \
  --server="https://cgrptmcip01.cloud.cammis.ca.gov" \
  deploy \
  --xauthtoken "$TOKEN" \
  --sourceInstanceId 3 \
  --targetInstanceId 1 \
  --projectName "Demo" \
  --labelId "$SOURCE_LABEL_ID" \
  --targetLabelName "PROMOTED-${BUILD_NUMBER}" > deploy.out 2>&1 || true

echo "=== Deploy Output (first 100 lines) ==="
sed -n '1,100p' deploy.out || true
echo "======================================="

# check for any obvious GraphQL errors
if grep -q '"errors"' deploy.out; then
  echo " MotioCI Deploy failed — see deploy.out above for details."
  exit 1
else
  echo " MotioCI Deploy stage completed successfully."
fi
        '''
      }
    }
  }
}
