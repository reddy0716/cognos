stage('Source checks (DEV/TEST)') {
  steps {
    container('python') {
      sh '''
        set -eu
        (set -o pipefail) 2>/dev/null || true

        cd MotioCI/api/CLI

        echo "1) List projects on Cognos-DEV/TEST (expect '${PROJECT_NAME}')"
        python3 ci-cli.py --server="$COGNOS_SERVER_URL" \
          project ls --xauthtoken="${MOTIO_AUTH_TOKEN}" --instanceName="Cognos-DEV/TEST" \
          | tee projects_src.txt

        if ! grep -i -F -q "${PROJECT_NAME}" projects_src.txt; then
          echo "ERROR: Project '${PROJECT_NAME}' not found on Cognos-DEV/TEST via MotioCI." >&2
          sed -n '1,200p' projects_src.txt
          exit 1
        fi
        echo "Project '${PROJECT_NAME}' is present on DEV/TEST."

        echo "2) List labels for '${PROJECT_NAME}' on Cognos-DEV/TEST (expect labelId=${LABEL_ID})"
        python3 ci-cli.py --server="$COGNOS_SERVER_URL" \
          label ls --xauthtoken="${MOTIO_AUTH_TOKEN}" \
          --instanceName="Cognos-DEV/TEST" --projectName="${PROJECT_NAME}" \
          | tee labels_src.txt

        # be lenient about output formats; look for the ID as a standalone number
        if ! grep -E -q "(^|[^0-9])${LABEL_ID}([^0-9]|$)" labels_src.txt; then
          echo "ERROR: Label id '${LABEL_ID}' not found in DEV/TEST project '${PROJECT_NAME}'." >&2
          sed -n '1,200p' labels_src.txt
          exit 1
        fi
        echo "Label id '${LABEL_ID}' exists on DEV/TEST."

        echo "Source DEV/TEST checks OK."
      '''
    }
    archiveArtifacts artifacts: 'MotioCI/api/CLI/projects_src.txt,MotioCI/api/CLI/labels_src.txt', onlyIfSuccessful: false
  }
}
