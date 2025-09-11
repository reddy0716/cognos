stage('MotioCI Login Check') {
  steps {
    container('python') {
      script {
        sh '''
set -euo pipefail
cd MotioCI/api/CLI

echo "=== Creating creds_check.json ==="

cat > creds_check.json <<ENDJSON
[
  {
    "instanceId": "3",
    "namespaceId": "LDAP",
    "username": "devtest_ldap_user",
    "password": "devtest_ldap_pass"
  },
  {
    "instanceId": "1",
    "namespaceId": "LDAP",
    "username": "prd_ldap_user",
    "password": "prd_ldap_pass"
  }
]
ENDJSON

echo "=== Creds file created (password masked) ==="
sed -E 's/"password":[^,}]*/"password":"***"/g' creds_check.json

echo "=== Running MotioCI login ==="
python3 ci-cli.py --server="$MOTIO_SERVER" login --credentialsFile creds_check.json > login_check.out 2>&1 || true

echo "=== Raw login_check.out (sanitized) ==="
sed -E 's/"password":[^,}]*/"password":"***"/g' login_check.out | sed -n '1,120p'

echo "=== End of login check output ==="
        '''
      }
    }
  }
}
