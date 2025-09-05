/* 
=======================================================================================
This file is being updated constantly by the DevOps team to introduce new enhancements
based on the template. If you have suggestions for improvement,
please contact the DevOps team so that we can incorporate the changes into the
template. In the meantime, if you have made changes here or don't want this file to be
updated, please indicate so at the beginning of this file.
=======================================================================================
*/

// variables from IBM template
def branch = env.BRANCH_NAME ?: "DEV"
def namespace = env.NAMESPACE ?: "dev"
def cloudName = env.CLOUD_NAME == "openshift" ? "openshift" : "kubernetes"
def workingDir = "/home/jenkins/agent"

APP_NAME = "combined-devops-cognos-deployments"

pipeline {
  agent {
    kubernetes {
      yaml """
apiVersion: v1
kind: Pod
spec:
  serviceAccountName: jenkins
  volumes:
    - name: dockersock
      hostPath:
        path: /var/run/docker.sock
    - emptyDir: {}
      name: varlibcontainers
    - name: jenkins-trusted-ca-bundle
      configMap:
        name: jenkins-trusted-ca-bundle
        defaultMode: 420
        optional: true
  containers:
    - name: jnlp
      securityContext:
        privileged: true
      envFrom:
        - configMapRef:
            name: jenkins-agent-env
            optional: true
      env:
        - name: GIT_SSL_CAINFO
          value: "/etc/pki/tls/certs/ca-bundle.crt"
      volumeMounts:
        - name: jenkins-trusted-ca-bundle
          mountPath: /etc/pki/tls/certs
    - name: node
      image: registry.access.redhat.com/ubi8/nodejs-16:latest
      tty: true
      command: ["/bin/bash"]
      securityContext:
        privileged: true
      workingDir: ${workingDir}
      envFrom:
        - configMapRef:
            name: jenkins-agent-env
            optional: true
      env:
        - name: HOME
          value: ${workingDir}
        - name: BRANCH
          value: ${branch}
        - name: GIT_SSL_CAINFO
          value: "/etc/pki/tls/certs/ca-bundle.crt"
      volumeMounts:
        - name: jenkins-trusted-ca-bundle
          mountPath: /etc/pki/tls/certs
    - name: python
      image: 136299550619.dkr.ecr.us-west-2.amazonaws.com/cammisboto3:1.2.0
      tty: true
      command: ["/bin/bash"]
      securityContext:
        privileged: true
      workingDir: ${workingDir}
      envFrom:
        - configMapRef:
            name: jenkins-agent-env
            optional: true
      env:
        - name: HOME
          value: ${workingDir}
        - name: BRANCH
          value: ${branch}
        - name: GIT_SSL_CAINFO
          value: "/etc/pki/tls/certs/ca-bundle.crt"
      volumeMounts:
        - name: jenkins-trusted-ca-bundle
          mountPath: /etc/pki/tls/certs
"""
    }
  }

  environment {
    GIT_BRANCH         = "${BRANCH_NAME}"

    // MotioCI server (naming kept for minimal change)
    COGNOS_SERVER_URL  = "https://cgrptmcip01.cloud.cammis.ca.gov"

    // Cognos REST base (overridable)
    COGNOS_API_BASE    = "https://dhcsprodcognos.ca.analytics.ibm.com/api"

    // Deployment configuration
    COGNOS_NAMESPACE   = "AzureAD"
    SOURCE_INSTANCE_ID = "3"      // Cognos DEV/TEST
    TARGET_INSTANCE_ID = "1"      // Cognos PROD
    LABEL_ID           = "57"
    PROJECT_NAME       = "Demo"
  }

  options {
    disableConcurrentBuilds()
    timestamps()
  }

  stages {
    stage("initialize") {
      steps {
        script {
          echo "Branch: ${env.GIT_BRANCH}"
          echo "Initializing Motio pipeline..."
          echo "=== Configuration Validation ==="
          echo "MotioCI Server: ${env.COGNOS_SERVER_URL}"
          echo "Cognos API Base: ${env.COGNOS_API_BASE}"
          echo "Cognos Namespace: ${env.COGNOS_NAMESPACE}"
          echo "Source Instance ID: ${env.SOURCE_INSTANCE_ID}"
          echo "Target Instance ID: ${env.TARGET_INSTANCE_ID}"
          echo "Label ID: ${env.LABEL_ID}"
          echo "Project Name: ${env.PROJECT_NAME}"
          echo "================================"
        }
      }
    }

    stage('Check Python Availability') {
      steps {
        container('python') {
          sh '''
            set -e
            echo "Checking for Python3..."
            which python3 || true
            python3 --version || true
          '''
        }
      }
    }

    stage('MotioCI Login') {
      steps {
        withCredentials([
          file(credentialsId: 'prod-credentials-json', variable: 'CREDENTIALS_FILE')
        ]) {
          container('python') {
            sh '''
              set -eu
              (set -o pipefail) 2>/dev/null || true

              echo "Installing MotioCI CLI dependencies..."
              cd MotioCI/api/CLI
              python3 -m pip install --user -r requirements.txt
              cd - >/dev/null

              echo "Logging into MotioCI with stored credentials file..."
              TOKEN_RAW=$(cd MotioCI/api/CLI && python3 ci-cli.py --server="$COGNOS_SERVER_URL" login --credentialsFile "$CREDENTIALS_FILE" | tail -n1)
              # strip optional "Auth Token:" prefix, spaces
              TOKEN_CLEAN=$(printf "%s" "$TOKEN_RAW" | sed -E 's/^[A-Za-z ]*:?[[:space:]]*//')
              if [ -z "$TOKEN_CLEAN" ]; then
                echo "ERROR: MotioCI login did not return a token."; exit 1
              fi
              # export for later steps
              printf "MOTIO_AUTH_TOKEN=%s\n" "$TOKEN_CLEAN" > MotioCI/api/motio_token.env
            '''
            script {
              def t = readFile('MotioCI/api/motio_token.env').trim()
              t.split("\n").each { line -> def (k,v) = line.split('=',2); env[k]=v }
              echo "MotioCI login completed - token captured."
            }
          }
        }
      }
    }

    stage('Auth: Cognos API session (API key)') {
      steps {
        container('python') {
          withCredentials([string(credentialsId: 'cognos-api-key-prd', variable: 'COGNOS_API_KEY')]) {
            sh '''
              set -eu
              (set -o pipefail) 2>/dev/null || true

              echo "Installing MotioCI CLI dependencies..."
              cd MotioCI/api/CLI
              python3 -m pip install --user -r requirements.txt
              cd - >/dev/null

              echo "Starting Cognos API session (PRD)..."
              BASE="${COGNOS_API_BASE}/v1"

              rm -f login.json session.json session.redacted.json headers.txt cookies.txt extensions.json || true
              mkdir -p MotioCI/api

              # Step 1: PUT /session with API key
              cat > login.json <<JSON
{ "parameters": [ { "name": "CAMAPILoginKey", "value": "${COGNOS_API_KEY}" } ] }
JSON

              curl --fail-with-body -sS -X PUT "$BASE/session" \
                   -H "Content-Type: application/json" \
                   -d @login.json \
                   -c cookies.txt -b cookies.txt \
                   -D headers.txt -o session.json

              SESSION_KEY=$(python3 -c 'import json; print(json.load(open("session.json")).get("session_key",""))')
              if [ -z "$SESSION_KEY" ]; then
                echo "ERROR: No session_key returned from Cognos." >&2
                echo "Response body:"; cat session.json || true
                exit 1
              fi

              # Build IBM-BA-Authorization header safely
              case "$SESSION_KEY" in
                "CAM "*) AUTH_VALUE="$SESSION_KEY" ;;
                "CAM"*)  AUTH_VALUE="$SESSION_KEY" ;;
                *)       AUTH_VALUE="CAM $SESSION_KEY" ;;
              esac

              # Step 2: GET /session WITH AUTH to obtain XSRF-TOKEN cookie (if issued)
              curl --fail-with-body -sS "$BASE/session" \
                   -H "IBM-BA-Authorization: $AUTH_VALUE" \
                   -H "Accept: application/json" \
                   -c cookies.txt -b cookies.txt \
                   -D headers.txt -o /dev/null

              # Extract XSRF token (cookie jar first, then Set-Cookie header)
              XSRF=$(awk '$1 ~ /^#HttpOnly_/ {sub("^#HttpOnly_", "", $1)} $6=="XSRF-TOKEN" {print $7}' cookies.txt | tail -n1 || true)
              if [ -z "${XSRF:-}" ]; then
                XSRF=$(awk 'BEGIN{IGNORECASE=1} /^Set-Cookie:/ && $0 ~ /XSRF-TOKEN/ {match($0,/XSRF-TOKEN=([^;]+)/,m); if (m[1]) print m[1] }' headers.txt | tail -n1 || true)
              fi

              # Step 3: sanity call → /extensions
              set -- -H "IBM-BA-Authorization: $AUTH_VALUE" -H "Accept: application/json"
              if [ -n "${XSRF:-}" ]; then
                set -- "$@" -H "X-XSRF-TOKEN: ${XSRF}"
              fi

              curl --fail-with-body -sS "$BASE/extensions" \
                   "$@" \
                   -c cookies.txt -b cookies.txt \
                   -D headers.txt -o extensions.json

              echo "Cognos API session verified."

              # Persist for later stages (quote values!)
              printf "COGNOS_SESSION_KEY='%s'\n" "$SESSION_KEY" >  MotioCI/api/motio_env
              printf "COGNOS_XSRF='%s'\n"       "${XSRF:-}"     >> MotioCI/api/motio_env
              printf "COGNOS_AUTH_VALUE='%s'\n" "$AUTH_VALUE"   >> MotioCI/api/motio_env

              # Redact session.json for safe archiving
              python3 -c 'import json; j=json.load(open("session.json")); j["session_key"]="***redacted***" if "session_key" in j else None; open("session.redacted.json","w").write(json.dumps(j,indent=2))'
            '''
          }
        }
        script {
          def envFile = readFile('MotioCI/api/motio_env').trim()
          envFile.split("\n").each { line ->
            def (k,v) = line.split('=', 2)
            if (v.startsWith("'") && v.endsWith("'") && v.length() >= 2) { v = v.substring(1, v.length()-1) }
            env[k] = v
          }
          echo "Auth stage complete: session_key (+ XSRF if issued) ready."
        }
        archiveArtifacts artifacts: 'login.json,session.redacted.json,headers.txt,cookies.txt,extensions.json', onlyIfSuccessful: false
      }
    }

    stage('Pre-deploy health checks (Cognos REST + MotioCI)') {
      steps {
        container('python') {
          sh '''
            set -eu
            (set -o pipefail) 2>/dev/null || true

            # Require auth artifacts from prior stages
            [ -f MotioCI/api/motio_env ] || { echo "Missing MotioCI/api/motio_env (run Auth + MotioCI login first)"; exit 1; }
            . MotioCI/api/motio_env

            BASE="${COGNOS_API_BASE}/v1"

            echo "1) Cognos REST check: /extensions with session_key"
            set -- -H "IBM-BA-Authorization: ${COGNOS_AUTH_VALUE}" -H "Accept: application/json"
            if [ -n "${COGNOS_XSRF:-}" ]; then
              set -- "$@" -H "X-XSRF-TOKEN: ${COGNOS_XSRF}"
            fi

            curl -sS --fail-with-body "$BASE/extensions" \
                 "$@" \
                 -c cookies.txt -b cookies.txt \
                 -D headers.txt -o extensions.json

            EXT_OK=$(python3 - <<'PY'
import json,sys
try:
  data=json.load(open("extensions.json"))
  ok = isinstance(data,(dict,list)) and len(data)>0
  print(1 if ok else 0)
except Exception:
  print(0)
PY
)
            if [ "$EXT_OK" -ne 1 ]; then
              echo "ERROR: /extensions returned empty or invalid JSON." >&2
              exit 1
            fi
            echo "Cognos REST OK."

            echo "2) MotioCI project presence on PRD"
            cd MotioCI/api/CLI

            python3 ci-cli.py --server="$COGNOS_SERVER_URL" \
              project ls --xauthtoken="${MOTIO_AUTH_TOKEN}" --instanceName="Cognos-PRD" \
              > projects_prd.json

            PROJ_OK=$(python3 - <<'PY'
import json,os
try:
  data=json.load(open("projects_prd.json"))
  target=os.environ.get("PROJECT_NAME","Demo")
  items = data.get("items") if isinstance(data,dict) else data
  if items is None: items = data.get("projects") if isinstance(data,dict) else []
  found = False
  for it in (items or []):
    if isinstance(it,dict) and it.get("name")==target:
      found=True; break
  print(1 if found else 0)
except Exception:
  print(0)
PY
)
            if [ "$PROJ_OK" -ne 1 ]; then
              echo "ERROR: Project '${PROJECT_NAME}' not found on Cognos-PRD via MotioCI." >&2
              exit 1
            fi
            echo "MotioCI project OK."

            echo "3) MotioCI namespace check on PRD (expects '${NAMESPACE_ID:-AzureAD}')"
            cd - >/dev/null

            curl -sS --fail-with-body -X POST "${COGNOS_SERVER_URL}/api/graphql" \
              -H "Content-Type: application/json" \
              -H "x-auth-token: ${MOTIO_AUTH_TOKEN}" \
              -d '{"query":"query($id: Long!){ instance(id:$id){ namespaces { id name } } }", "variables":{"id":'"${TARGET_INSTANCE_ID:-1}"'}}' \
              > namespaces_prd.json

            NS_OK=$(python3 - <<'PY'
import json,os
try:
  d=json.load(open("namespaces_prd.json"))
  target=os.environ.get("NAMESPACE_ID","AzureAD")
  ns = (((d.get("data") or {}).get("instance") or {}).get("namespaces") or [])
  print(1 if any(isinstance(n,dict) and n.get("name")==target for n in ns) else 0)
except Exception:
  print(0)
PY
)
            if [ "$NS_OK" -ne 1 ]; then
              echo "ERROR: Namespace '${NAMESPACE_ID}' not found on PRD (MotioCI GraphQL)." >&2
              exit 1
            fi
            echo "Namespace OK."

            echo "Pre-deploy health checks passed."
          '''
        }
        archiveArtifacts artifacts: 'extensions.json,headers.txt,MotioCI/api/CLI/projects_prd.json,namespaces_prd.json', onlyIfSuccessful: false
      }
    }

  stage('Deploy (DEV/TEST → PRD)') {
  steps {
    container('python') {
      sh '''
        set -eu
        (set -o pipefail) 2>/dev/null || true

        # TEST ONLY: hardcoded CAMPassport. Do NOT commit this to source control.
        CAMPASSPORT='MTsxMDE6ZmFrZS1jYW1wYXNzcG9ydC12YWx1ZToxMjM0NTY3ODkwOzA7MzswOw=='

        # Optional: trust internal CA bundles if needed
        export PYTHONHTTPSVERIFY=0

        cd MotioCI/api/CLI

        echo "Testing token validity with instance list..."
        python3 ci-cli.py --server="$COGNOS_SERVER_URL" instance ls --xauthtoken="${MOTIO_AUTH_TOKEN}" || echo "Token validation failed"

        echo "Testing PROD project access..."
        python3 ci-cli.py --server="$COGNOS_SERVER_URL" project ls --xauthtoken="${MOTIO_AUTH_TOKEN}" --instanceName="Cognos-PRD" || echo "PROD project access failed"

        echo "Executing deployment..."
        TIMESTAMP=$(date +%Y%m%d-%H%M)
        python3 ci-cli.py --server="$COGNOS_SERVER_URL" deploy \
          --xauthtoken="${MOTIO_AUTH_TOKEN}" \
          --sourceInstanceId="${SOURCE_INSTANCE_ID}" \
          --targetInstanceId="${TARGET_INSTANCE_ID}" \
          --labelId="${LABEL_ID}" \
          --projectName="${PROJECT_NAME}" \
          --targetLabelName="PROMOTED-${TIMESTAMP}" \
          --camPassportId="${CAMPASSPORT}" \
          --namespaceId="${COGNOS_NAMESPACE}"

        DEPLOY_EXIT_CODE=$?
        if [ $DEPLOY_EXIT_CODE -eq 0 ]; then
          echo "SUCCESS! Deployment completed successfully!"
        else
          echo "ERROR: Deployment failed with exit code $DEPLOY_EXIT_CODE"
          exit $DEPLOY_EXIT_CODE
        fi

        echo "Verification: list PROD labels after promotion:"
        python3 ci-cli.py --server="$COGNOS_SERVER_URL" label ls --xauthtoken="${MOTIO_AUTH_TOKEN}" --instanceName="Cognos-PRD" --projectName="${PROJECT_NAME}" || echo "Failed to list PROD labels after promotion"
      '''
    }
  }
}

  }

  post {
    always {
      echo "Pipeline execution finished."
    }
    success {
      echo "MotioCI pipeline completed successfully."
    }
    failure {
      echo "MotioCI pipeline failed."
    }
  }
}
