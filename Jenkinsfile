stage('MotioCI Login') {
  steps {
    container('python') {
      script {
        sh '''
set -euo pipefail
cd MotioCI/api/CLI
python3 -m pip install --user -r requirements.txt

# trust internal CA (if needed)
export REQUESTS_CA_BUNDLE=/etc/pki/tls/certs/ca-bundle.crt
export SSL_CERT_FILE=/etc/pki/tls/certs/ca-bundle.crt

echo "Logging into MotioCI..."
python3 ci-cli.py \
  --server="https://cgrptmcip01.cloud.cammis.ca.gov" \
  login \
  --credentials "[{'instanceId':1,'camPassportId':'AWk0N0VCMDhFMkY3NkM0QzhCQUQ0QzIyMjUxNDg1QjY4N9WL63EPWDOm8rcR9XxGG850b22r'},{'instanceId':3,'camPassportId':'AWk1OUMyRkU3M0U2RUM0RUZEQUQ3MTY4ODBEN0NFNDVBRWu4kJkLtIYGd/Lpzr7rmNMqQUHn'}]" \
  > login.out 2>&1 || true

echo "=== login.out ==="
sed -n '1,40p' login.out || true

# Extract token
awk 'match($0,/(Auth[[:space:]]*[Tt]oken|xauthtoken)[:=][[:space:]]*([A-Za-z0-9._-]+)/,m){print m[2]}' login.out | tail -n1 > login.token || true
if [ ! -s login.token ]; then
  awk 'match($0,/"(authToken|xauthtoken)"[[:space:]]*:[[:space:]]*"([^"]+)"/,m){print m[2]}' login.out | tail -n1 > login.token || true
fi

TOKEN=$(cat login.token 2>/dev/null || true)
if [ -z "${TOKEN:-}" ]; then
  echo "Login failed. Showing login.out..."
  sed -n '1,60p' login.out
  exit 1
fi

echo "MotioCI token captured (len=${#TOKEN})"
echo "$TOKEN" > ../token.txt
        '''
      }
    }
  }
}
