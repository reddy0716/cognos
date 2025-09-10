stage('Check Instance Permissions') {
  steps {
    container('python') {
      script {
        sh '''
set -euo pipefail
cd MotioCI/api/CLI

if [ -z "${MOTIO_AUTH_TOKEN:-}" ]; then
  echo "ERROR: MOTIO_AUTH_TOKEN is empty (login stage failed?)"
  exit 1
fi

SERVER="https://cgrptmcip01.cloud.cammis.ca.gov"
# PRD first, then DEV/TEST
INSTANCES=("Cognos-PRD" "Cognos-DEV/TEST")
PROJECT="Demo"

# Use mounted CA bundle (preferred)
export REQUESTS_CA_BUNDLE=/etc/pki/tls/certs/ca-bundle.crt
export SSL_CERT_FILE=/etc/pki/tls/certs/ca-bundle.crt
# Last resort only:
# export PYTHONHTTPSVERIFY=0

TIMEOUT=""
if command -v timeout >/dev/null 2>&1; then
  TIMEOUT="timeout 45s"
fi

overall_rc=0

run_check() {
  local what="$1"    # "projects" or "labels(project)"
  local inst="$2"
  local cmd="$3"     # ci-cli subcommand + args (no server/token)
  local tag="$(echo "$inst" | tr '/' '_')_${what}"

  ${TIMEOUT} python3 ci-cli.py --server="$SERVER" \
    ${cmd} \
    1>"/tmp/${tag}.out" 2>"/tmp/${tag}.err"
  rc=$? || true

  if [ $rc -ne 0 ] || grep -qiE "access is denied|unauthorized|forbidden|401|403|certificate verify failed|ssl:|ssLError" "/tmp/${tag}.err"; then
    echo "  !! FAIL: ${what} check failed on '${inst}'"
    echo "----- stderr (first 100 lines) -----"
    sed -n '1,100p' "/tmp/${tag}.err" || true
    echo "------------------------------------"
    return 1
  fi

  lines=$(wc -l < "/tmp/${tag}.out" || echo 0)
  echo "  OK: ${what} check on '${inst}' (output lines: ${lines})"
  return 0
}

for inst in "${INSTANCES[@]}"; do
  is_prd=0
  [ "$inst" = "Cognos-PRD" ] && is_prd=1

  echo ""
  echo "===== Checking permissions for instance: ${inst} ====="

  # 1) Basic instance access: projects list
  if ! run_check "projects" "${inst}" \
       "project ls --xauthtoken=\\"${MOTIO_AUTH_TOKEN}\\" --instanceName=\\"${inst}\\""
  then
    if [ $is_prd -eq 1 ]; then
      echo ""
      echo "PRD access failed — failing fast before checking DEV/TEST."
      exit 1
    fi
    overall_rc=1
    continue
  fi

  # 2) Light read: labels in Demo (warn-only)
  if ! run_check "labels(${PROJECT})" "${inst}" \
       "label ls --xauthtoken=\\"${MOTIO_AUTH_TOKEN}\\" --instanceName=\\"${inst}\\" --projectName=\\"${PROJECT}\\""
  then
    echo "  !! WARN: Could not read labels for '${PROJECT}' on '${inst}'"
  fi
done

if [ "$overall_rc" -ne 0 ]; then
  echo ""
  echo "At least one non-PRD instance failed basic access checks. Failing the build."
  exit $overall_rc
fi

echo ""
echo "All instances passed basic access checks."
'''
      }
    }
  }
}
