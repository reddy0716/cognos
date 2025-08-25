stage('MotioCI Login') {
  steps {
    withCredentials([file(credentialsId: 'prod-credentials-json', variable: 'CREDENTIALS_FILE')]) {
      container('python') {
        sh '''
          set -e
          cd MotioCI/api/CLI
          python3 -m pip install --user -r requirements.txt >/dev/null 2>&1 || true

          # Capture ONLY the token (our ci-cli.py now prints just the token on success)
          TOKEN=$(python3 ci-cli.py --server="https://cgrptmcip01.cloud.cammis.ca.gov" \
                    --non-interactive login --credentialsFile "$CREDENTIALS_FILE" \
                    | tail -n1 | tr -d '\\r')

          if [ -z "$TOKEN" ]; then
            echo "ERROR: Empty MotioCI token." >&2
            exit 1
          fi

          # Export for later stages
          echo "TOKEN=$TOKEN" > ../motio_env
        '''
      }
      script {
        def envFile = readFile('MotioCI/api/motio_env').trim()
        for (pair in envFile.split("\n")) {
          def (k,v) = pair.split("=", 2)
          env[k] = v
        }
        echo "MotioCI login completed - token captured."
      }
    }
  }
}
