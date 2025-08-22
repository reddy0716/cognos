stage('Deploy') {
  steps {
    container('python') {
      withCredentials([
        usernamePassword(credentialsId: 'cognos-azuread-user', usernameVariable: 'COG_USER', passwordVariable: 'COG_PASS')
      ]) {
        sh '''
          set -euo pipefail
          cd MotioCI/api/CLI

          COG_SERVER="https://cgrptmcip01.cloud.cammis.ca.gov"
          AUTH="${MOTIO_AUTH_TOKEN}"
          SRC_IID="3"
          SRC_LABEL="57"
          PROJ_NAME="Demo"
          TGT_IID="1"
          TGT_LABEL="PROMOTED-20250712-115"
          TGT_INSTANCE_NAME="Cognos-PRD"

          # --- GraphQL Deploy (no verbose; HTTP checked) ---
          # TODO: If you get "Unknown type DeployInput", update these two to the actual schema:
          MUTATION_NAME="deploy"            # e.g., deploy / promote / runDeployment / deployLabel
          INPUT_TYPE="DeployInput!"         # e.g., PromoteLabelInput! / RunDeploymentInput!

          read -r -d "" PAYLOAD <<JSON
          {
            "query": "mutation Run($input: ${INPUT_TYPE}) { ${MUTATION_NAME}(input: $input) { deployment { id } } }",
            "variables": {
              "input": {
                "source": {
                  "instanceId": "${SRC_IID}",
                  "label": "${SRC_LABEL}",
                  "projectName": "${PROJ_NAME}"
                },
                "target": {
                  "instanceId": "${TGT_IID}",
                  "targetLabelName": "${TGT_LABEL}",
                  "userName": "${COG_USER}",
                  "password": "${COG_PASS}",
                  "namespaceId": "AzureAD"
                }
              }
            }
          }
          JSON

          # If your agent trusts your internal CA, remove -k
          DEPLOY_HTTP=$(curl -sS -k -o deploy.json -w "%{http_code}" \
            -H "Content-Type: application/json" \
            -H "x-auth-token: ${AUTH}" \
            -X POST "${COG_SERVER}/api/graphql" \
            -d "$PAYLOAD")

          echo "Deploy HTTP status: $DEPLOY_HTTP"
          # Show compact body (no secrets inside)
          if command -v jq >/dev/null 2>&1; then jq -c . deploy.json || cat deploy.json; else cat deploy.json; fi

          # Fail hard on HTTP error codes
          case "$DEPLOY_HTTP" in
            200|202|303) : ;;    # accepted/ok responses
            *) echo "Deploy request failed (HTTP $DEPLOY_HTTP)"; exit 1;;
          esac

          # --- Verify in PRD by polling for the target label ---
          echo "Verifying label '${TGT_LABEL}' in ${TGT_INSTANCE_NAME}/${PROJ_NAME}…"
          attempt=0
          max_attempts=18   # ~3 minutes (18 * 10s)
          sleep_seconds=10

          until python3 ci-cli.py --server="${COG_SERVER}" \
                label ls --xauthtoken="${AUTH}" \
                --instanceName="${TGT_INSTANCE_NAME}" --projectName="${PROJ_NAME}" \
                | grep -q "\\\"name\\\": \\\"${TGT_LABEL}\\\""
          do
            attempt=$((attempt+1))
            if [ "$attempt" -ge "$max_attempts" ]; then
              echo "❌ Deployment label '${TGT_LABEL}' not found after $((attempt*sleep_seconds))s."
              echo "Existing labels:"
              python3 ci-cli.py --server="${COG_SERVER}" \
                label ls --xauthtoken="${AUTH}" \
                --instanceName="${TGT_INSTANCE_NAME}" --projectName="${PROJ_NAME}" || true
              exit 1
            fi
            echo "Label not visible yet; retry ${attempt}/${max_attempts} in ${sleep_seconds}s…"
            sleep "$sleep_seconds"
          done

          echo "✅ Verified: '${TGT_LABEL}' present in ${TGT_INSTANCE_NAME}/${PROJ_NAME}."
        '''
      }
    }
  }
}
