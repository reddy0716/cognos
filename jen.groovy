  parameters {
    choice(name: 'SOURCE_ENV', choices: ['DEV','SIT','UAT'], description: 'Source Environment')
    choice(name: 'TARGET_ENV', choices: ['SIT','UAT','PROD'], description: 'Target Environment')
    string(name: 'PROJECT_NAME', defaultValue: 'Demo', description: 'MotioCI Project Name')
    string(name: 'OBJECT_PATH', defaultValue: '', description: 'Optional: Folder or Report path (leave blank for full project)')
  }


echo """
        =====================================================
        MotioCI Cognos Deployment Pipeline
        Source: ${params.SOURCE_ENV}
        Target: ${params.TARGET_ENV}
        Project: ${params.PROJECT_NAME}
        Object Path: ${params.OBJECT_PATH ?: 'FULL PROJECT'}
        =====================================================
        """



stage('Validate Source Content') {
      steps {
        container('python') {
          script {
            if (params.OBJECT_PATH?.trim()) {
              sh '''
                set -e
                cd MotioCI/api/CLI
                TOKEN=$(cat ../../token.txt)
                echo "Checking that object exists in source project..."
                python3 ci-cli.py object list \
                  --server="$MOTIO_SERVER" \
                  --project "$PROJECT_NAME" \
                  --instanceName "$SOURCE_INSTANCE" \
                  --xauthtoken "$TOKEN" | grep "${OBJECT_PATH}" || {
                    echo "Object ${OBJECT_PATH} not found in source project."; exit 1; }
                echo "Object exists in source; ready to promote."
              '''
            } else {
              echo "OBJECT_PATH not provided — will deploy entire project."
            }
          }
        }
      }
    }
