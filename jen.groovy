stage('Deploy') {
  steps {
    withCredentials([usernamePassword(credentialsId: 'cognos-service-user', usernameVariable: 'COG_USER', passwordVariable: 'COG_PASS')]) {
      container('python') {
        sh '''
          set -euo pipefail
          cd MotioCI/api/CLI

          echo "Sanity: list projects on Cognos-PRD..."
          python3 ci-cli.py --server="https://cgrptmcip01.cloud.cammis.ca.gov" \
            project ls --xauthtoken="${TOKEN}" --instanceName="Cognos-PRD"

          # Do the deployment (adjust ids/names as needed)
          python3 ci-cli.py --server="https://cgrptmcip01.cloud.cammis.ca.gov" \
            --non-interactive deploy \
            --xauthtoken="${TOKEN}" \
            --sourceInstanceId=3 \
            --targetInstanceId=1 \
            --labelId=57 \
            --projectName="Demo" \
            --targetLabelName="PROMOTED-20250712-115" \
            --username="${COG_USER}" \
            --password="${COG_PASS}" \
            --namespaceId="azure"

          echo "Verification: labels after deployment"
          python3 ci-cli.py --server="https://cgrptmcip01.cloud.cammis.ca.gov" \
            label ls --xauthtoken="${TOKEN}" --instanceName="Cognos-PRD" --projectName="Demo" \
            | tee verify_labels.json
        '''
      }
    }
  }
}
