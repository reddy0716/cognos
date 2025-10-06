stage('MotioCI Login') {
  steps {
    container('python') {
      script {
        // install dependencies
        sh '''
set -e
cd MotioCI/api/CLI
python3 -m pip install --user -r requirements.txt
echo "Successfully installed packages"
        '''

        // login and capture token
        sh '''
set -euo pipefail
cd MotioCI/api/CLI

echo "Creating creds.json with both DEV/TEST and PRD credentials..."

# heredoc delimiters MUST start at column 0
cat <<ENDJSON > creds.json
[
  {
    "instanceId": "1",
    "namespaceId": "AzureAD",
    "username": "CMarksSS01@intra.dhs.ca.gov",
    "camPassportId": "AWk0N0VCMDhFMkY3NkM0QzhCQUQ0QzIyMjUxNDg1QjY4N9WL63EPWDOm8rcR9XxGG850b22r"
  },
  {
    "instanceId": "3",
    "namespaceId": "AzureAD",
    "username": "CMarksSS01@intra.dhs.ca.gov",
    "camPassportId": "AWk1OUMyRkU3M0U2RUM0RUZEQUQ3MTY4ODBEN0NFNDVBRWu4kJkLtIYGd/Lpzr7rmNMqQUHn"
  }
]
ENDJSON

python3 ci-cli.py --server="https://cgrptmcip01.cloud.cammis.ca.gov" \
  login --credentialsFile creds.json > login.out 2>&1 || true

# Extract token
awk 'match($0,/(Auth[[:space:]]*[Tt]oken|xauthtoken)[:=][[:space:]]*([A-Za-z0-9._-]+)/,m){print m[2]}' login.out | tail -n1 > login.token || true
if [ ! -s login.token ]; then
  awk 'match($0,/"(authToken|xauthtoken)"[[:space:]]*:[[:space:]]*"([^"]+)"/,m){print m[2]}' login.out | tail -n1 > login.token || true
fi

TOKEN=$(cat login.token 2>/dev/null || true)
if [ -z "${TOKEN:-}" ]; then
  echo "Login failed. Showing first lines of login.out:"
  sed -E 's/"camPassportId":[^,}]*/"camPassportId":"***"/g' login.out | sed -n '1,60p'
  exit 1
fi

echo "MotioCI token captured (len=${#TOKEN})"
echo "$TOKEN" > ../token.txt
        '''
      }
    }
  }
}
