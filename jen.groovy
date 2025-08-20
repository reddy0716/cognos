stage('List Namespaces (PROD)') {
  steps {
    container('python') {
      sh '''
        set -euo pipefail
        export PATH="$HOME/.local/bin:$PATH"
        cd MotioCI/api/CLI
        python3 - <<'PY'
import os, requests, json, queries
CI_URL   = os.environ['COGNOS_SERVER_URL']
GRAPH_URL = CI_URL + "/graphql"
headers   = {"x-auth-token": os.environ['MOTIO_AUTH_TOKEN_PROD']}
vars      = {"id": int(os.environ['PROD_INSTANCE_ID'])}

r = requests.post(GRAPH_URL, headers=headers,
                  json={"query": queries.GET_VERSIONED_NAMESPACES, "variables": vars},
                  verify=False)
print("=== PROD Namespaces ===")
try:
    data = r.json()["data"]["instance"]["namespaces"]
    for ns in data:
        print(json.dumps(ns))
except Exception:
    print("Failed to parse namespaces. Raw response:")
    try:
        print(r.text)
    except Exception:
        pass
PY
      '''
    }
  }
}

stage('List Namespaces (DEV)') {
  steps {
    container('python') {
      sh '''
        set -euo pipefail
        export PATH="$HOME/.local/bin:$PATH"
        cd MotioCI/api/CLI
        python3 - <<'PY'
import os, requests, json, queries
CI_URL   = os.environ['COGNOS_SERVER_URL']
GRAPH_URL = CI_URL + "/graphql"
headers   = {"x-auth-token": os.environ['MOTIO_AUTH_TOKEN_DEV']}
vars      = {"id": int(os.environ['DEVTEST_INSTANCE_ID'])}

r = requests.post(GRAPH_URL, headers=headers,
                  json={"query": queries.GET_VERSIONED_NAMESPACES, "variables": vars},
                  verify=False)
print("=== DEV/TEST Namespaces ===")
try:
    data = r.json()["data"]["instance"]["namespaces"]
    for ns in data:
        # Print the exact 'id' you must pass as --namespaceId
        print(json.dumps(ns))
except Exception:
    print("Failed to parse namespaces. Raw response:")
    try:
        print(r.text)
    except Exception:
        pass
PY
      '''
    }
  }
}
