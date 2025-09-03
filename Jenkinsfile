// ------------------ PARAMETERS YOU CAN TUNE ------------------
def MOTIO_URL            = "https://cgrptmcip01.cloud.cammis.ca.gov"
def SOURCE_INSTANCE_NAME = "Cognos-DEV/TEST"
def TARGET_INSTANCE_NAME = "Cognos-PRD"
def PROJECT_NAME         = "Demo"
def SOURCE_LABEL_ID      = "57"                    // set per promotion
def TARGET_LABEL_NAME    = "PROMOTED-20250712-115" // set per promotion
def NAMESPACE_ID         = "AzureAD"               // or "azure" if that's your real id
// -------------------------------------------------------------

properties([
  parameters([
    booleanParam(name: 'USE_CAM_PASSPORT', defaultValue: false,
      description: 'If true, use CAM Passport instead of username/password'),
    text(name: 'CAM_PASSPORT_ID', defaultValue: '',
      description: 'Paste CAM Passport ID if USE_CAM_PASSPORT=true')
  ])
])

stages {
  stage('Login (PROD)') {
    steps {
      withCredentials([file(credentialsId: 'prod-credentials-json', variable: 'CREDENTIALS_FILE')]) {
        container('python') {
          sh """
            set -euo pipefail
            cd MotioCI/api/CLI
            # deps (idempotent; safe if already installed)
            python3 -m pip install --user -r requirements.txt >/dev/null 2>&1 || true

            # Your CLI prints ONLY the token; capture it exactly
            TOKEN=\$(python3 ci-cli.py --server='${MOTIO_URL}' login --credentialsFile "\$CREDENTIALS_FILE" | tr -d '[:space:]')
            test -n "\$TOKEN" || { echo 'ERROR: login returned empty token'; exit 1; }
            echo "PROD token acquired (****\${TOKEN: -6})"
            echo "\$TOKEN" > ${WORKSPACE}/.motio_token
          """
        }
      }
    }
  }

  stage('Preflight (optional but helpful)') {
    steps {
      container('python') {
        sh """
          set -euo pipefail
          cd MotioCI/api/CLI
          TOKEN=\$(cat ${WORKSPACE}/.motio_token)

          echo "== Instances (token scope) =="
          python3 ci-cli.py --server='${MOTIO_URL}' instance ls --xauthtoken="\$TOKEN"

          echo "== Target projects on ${TARGET_INSTANCE_NAME} =="
          python3 ci-cli.py --server='${MOTIO_URL}' project ls --xauthtoken="\$TOKEN" --instanceName='${TARGET_INSTANCE_NAME}'

          echo "== Source labels on ${SOURCE_INSTANCE_NAME} / ${PROJECT_NAME} (may fail if token cannot read source) =="
          python3 ci-cli.py --server='${MOTIO_URL}' label ls --xauthtoken="\$TOKEN" --instanceName='${SOURCE_INSTANCE_NAME}' --projectName='${PROJECT_NAME}' || true
        """
      }
    }
  }

  stage('Deploy DEV → PRD') {
    when {
      anyOf {
        allOf { expression { return !params.USE_CAM_PASSPORT } }
        allOf { expression { return params.USE_CAM_PASSPORT && params.CAM_PASSPORT_ID?.trim() } }
      }
    }
    steps {
      script {
        if (!params.USE_CAM_PASSPORT) {
          // ---- Path A: username/password auth to target ----
          withCredentials([usernamePassword(credentialsId: 'prod-cognos-portal-user', usernameVariable: 'PORTAL_USER', passwordVariable: 'PORTAL_PASS')]) {
            container('python') {
              sh """
                set -euo pipefail
                cd MotioCI/api/CLI
                TOKEN=\$(cat ${WORKSPACE}/.motio_token)

                echo "Running deploy with username/password auth..."
                python3 ci-cli.py --server='${MOTIO_URL}' deploy \\
                  --non-interactive \\
                  --xauthtoken="\$TOKEN" \\
                  --sourceInstanceName='${SOURCE_INSTANCE_NAME}' \\
                  --labelId='${SOURCE_LABEL_ID}' \\
                  --targetInstanceName='${TARGET_INSTANCE_NAME}' \\
                  --projectName='${PROJECT_NAME}' \\
                  --targetLabelName='${TARGET_LABEL_NAME}' \\
                  --username="\$PORTAL_USER" \\
                  --password="\$PORTAL_PASS" \\
                  --namespaceId='${NAMESPACE_ID}'

                echo "Verify labels on PRD:"
                python3 ci-cli.py --server='${MOTIO_URL}' label ls --xauthtoken="\$TOKEN" --instanceName='${TARGET_INSTANCE_NAME}' --projectName='${PROJECT_NAME}'
              """
            }
          }
        } else {
          // ---- Path B: CAM Passport auth to target ----
          container('python') {
            sh """
              set -euo pipefail
              cd MotioCI/api/CLI
              TOKEN=\$(cat ${WORKSPACE}/.motio_token)

              CAM_PASSPORT="\$(printf '%s' "${params.CAM_PASSPORT_ID}" | tr -d '\\n' | tr -d '\\r')"
              test -n "\$CAM_PASSPORT" || { echo 'ERROR: CAM_PASSPORT_ID is empty'; exit 1; }

              echo "Running deploy with CAM Passport auth..."
              python3 ci-cli.py --server='${MOTIO_URL}' deploy \\
                --non-interactive \\
                --xauthtoken="\$TOKEN" \\
                --sourceInstanceName='${SOURCE_INSTANCE_NAME}' \\
                --labelId='${SOURCE_LABEL_ID}' \\
                --targetInstanceName='${TARGET_INSTANCE_NAME}' \\
                --projectName='${PROJECT_NAME}' \\
                --targetLabelName='${TARGET_LABEL_NAME}' \\
                --camPassportId="\$CAM_PASSPORT" \\
                --namespaceId='${NAMESPACE_ID}'

              echo "Verify labels on PRD:"
              python3 ci-cli.py --server='${MOTIO_URL}' label ls --xauthtoken="\$TOKEN" --instanceName='${TARGET_INSTANCE_NAME}' --projectName='${PROJECT_NAME}'
            """
          }
        }
      }
    }
  }
}

post {
  success { echo "✅ Deploy complete: ${TARGET_LABEL_NAME}" }
  failure { echo "❌ Deploy failed — check the stage logs above for the first error." }
}
