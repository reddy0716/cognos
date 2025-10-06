stage('MotioCI Login') {
  steps {
    container('python') {
      script {
        sh '''
set -euo pipefail
cd MotioCI/api/CLI

echo "Installing MotioCI CLI dependencies..."
python3 -m pip install --user -r requirements.txt

echo "Creating credentials JSON for DEV/TEST and PRD..."
cat <<ENDJSON > creds.json
[
  {
    "instanceId": "1",
    "camPassportId": "AWk0N0VCMDhFMkY3NkM0QzhCQUQ0QzIyMjUxNDg1QjY4N9WL63EPWDOm8rcR9XxGG850b22r"
  },
  {
    "instanceId": "3",
    "camPassportId": "AWk1OUMyRkU3M0U2RUM0RUZEQUQ3MTY4ODBEN0NFNDVBRWu4kJkLtIYGd/Lpzr7rmNMqQUHn"
  }
]
ENDJSON

echo "Logging in to MotioCI..."
python3 ci-cli.py \
  --server="https://cgrptmcip01.cloud.cammis.ca.gov" \
  login --credentialsFile creds.json > login.out 2>&1 || true

echo "=== login.out (first 40 lines) ==="
sed -n '1,40p' login.out || true
echo "================================="

# Extract Auth Token
awk 'match($0,/(Auth[[:space:]]*[Tt]oken|x-auth_token|xauthtoken)[[:space:]]*[:=][[:space:]]*([A-Za-z0-9._-]+)/,m){print m[2]}' login.out | tail -n1 > login.token || true

TOKEN=$(cat login.token 2>/dev/null || true)
if [ -z "${TOKEN:-}" ]; then
  echo "Login failed. Showing masked output for debugging..."
  sed -E 's/"camPassportId":[^,}]*/"camPassportId":"***"/g' login.out | sed -n '1,80p'
  exit 1
fi

echo "MotioCI token captured (len=${#TOKEN})"
echo "$TOKEN" > ../token.txt
        '''
      }
    }
  }
}
