stage('Prepare Deployment') {
  when {
    expression {
      SURGE_ENV != "NONE"
    }
  }
  steps {
    container(name: "aws-boto3") {
      script {

        def surgeEnv = SURGE_ENV_CONFIG[SURGE_ENV]

        sh """#!/bin/bash
          echo "Setting up app directories with files, or deployment will fail"
          mkdir -p devops/codedeploy/surgeapi
          touch devops/codedeploy/surgeapi/placeholder.txt

          echo "Replacing tokenized values for accessing Vault"

          # ===== SAFE TOKEN REPLACEMENT (WITH QUOTES) =====
          sed -i "s|\\"{VAULT_ADDR}\\"|\\"${VAULT_ADDR["${SURGE_ENV}"]}\\"|g" devops/codedeploy/environment/deploy-environment.ps1
          sed -i "s|\\"{VAULT_SECRET_PATH}\\"|\\"${VAULT_SECRET_PATH["${SURGE_ENV}"]}\\"|g" devops/codedeploy/environment/deploy-environment.ps1
          sed -i "s|\\"{VAULT_SECRET_PATH_LTAR}\\"|\\"${VAULT_SECRET_PATH_LTAR["${SURGE_ENV}"]}\\"|g" devops/codedeploy/environment/deploy-environment.ps1
          sed -i "s|\\"{VAULT_SECRET_PATH_IMGVWR}\\"|\\"${VAULT_SECRET_PATH_IMGVWR["${SURGE_ENV}"]}\\"|g" devops/codedeploy/environment/deploy-environment.ps1
          sed -i "s|\\"{VAULT_APPROLE_AUTH_PATH}\\"|\\"${VAULT_APPROLE_AUTH_PATH}\\"|g" devops/codedeploy/environment/deploy-environment.ps1

          sed -i "s|\\"{SURGE_ENVNAME}\\"|\\"${surgeEnv["SURGE_ENVNAME"]}\\"|g" devops/codedeploy/environment/deploy-environment.ps1
          sed -i "s|\\"{SURGE_RPM_ROOT}\\"|\\"${surgeEnv["SURGE_RPM_ROOT"]}\\"|g" devops/codedeploy/environment/deploy-environment.ps1

          # after-install flag
          sed -i "s|{DEPLOY_ENVIRONMENT}|${env_DEPLOY_ENVIRONMENT}|g" devops/codedeploy/after-install.bat
        """

        if ("${SURGE_ENV}" != "PRD") {

          withCredentials([string(credentialsId: 'APPROLE_ROLE_ID', variable: 'APPROLE_ROLE_ID')]) {
            sh """#!/bin/bash
              sed -i "s|\\"{APPROLE_ROLE_ID}\\"|\\"${APPROLE_ROLE_ID}\\"|g" devops/codedeploy/environment/deploy-environment.ps1
            """
          }

          withCredentials([string(credentialsId: 'APPROLE_SECRET_ID', variable: 'APPROLE_SECRET_ID')]) {
            sh """#!/bin/bash
              sed -i "s|\\"{APPROLE_SECRET_ID}\\"|\\"${APPROLE_SECRET_ID}\\"|g" devops/codedeploy/environment/deploy-environment.ps1
            """
          }

        } else {

          withCredentials([string(credentialsId: 'APPROLE_ROLE_ID_PRD', variable: 'APPROLE_ROLE_ID')]) {
            sh """#!/bin/bash
              sed -i "s|\\"{APPROLE_ROLE_ID}\\"|\\"${APPROLE_ROLE_ID}\\"|g" devops/codedeploy/environment/deploy-environment.ps1
            """
          }

          withCredentials([string(credentialsId: 'APPROLE_SECRET_ID_PRD', variable: 'APPROLE_SECRET_ID')]) {
            sh """#!/bin/bash
              sed -i "s|\\"{APPROLE_SECRET_ID}\\"|\\"${APPROLE_SECRET_ID}\\"|g" devops/codedeploy/environment/deploy-environment.ps1
            """
          }
        }

      } // script
    } // container
  } // steps
}
