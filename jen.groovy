stage("Prepare Deployment") {
  steps {
    container(name: "aws-boto3") {
      script {
        lock(resource: 'deployments-github-repo', inversePrecedence: false) {
          dir("${WORKSPACE}/deployrepo") {
            withCredentials([usernamePassword(credentialsId: "github-key", usernameVariable: 'NUSER', passwordVariable: 'NPASS')]) {

              // --- Determine Source Environment ---
              def sourceEnv = ""
              if (env_promotion_to_environment == "sit") {
                sourceEnv = "dev"
              } else if (env_promotion_to_environment == "uat") {
                sourceEnv = "sit"
              } else if (env_promotion_to_environment == "prd") {
                sourceEnv = "uat"
              } else {
                error("Invalid promotion target environment selected.")
              }

              sh """
                echo "=============================="
                echo "Promoting from ${sourceEnv} → ${env_promotion_to_environment}"
                echo "=============================="

                git clone https://${NUSER}:${NPASS}@github.com/ca-mmis/deployments-combined-devops.git --depth=1
                cd deployments-combined-devops
                git config --global user.email "jenkins@cammis.com"
                git config --global user.name "jenkins"
                git checkout master
                git pull

                echo "Preparing target folder..."
                mkdir -p tar-surge-client/${env_promotion_to_environment}/Thickclient
                rm -rf tar-surge-client/${env_promotion_to_environment}/Thickclient/*

                echo "Copying artifacts from ${sourceEnv}/Thickclient → ${env_promotion_to_environment}/Thickclient"
                cp -a tar-surge-client/${sourceEnv}/Thickclient/. tar-surge-client/${env_promotion_to_environment}/Thickclient/

                echo "Promotion completed from ${sourceEnv} → ${env_promotion_to_environment} on \$(date)" > tar-surge-client/${env_promotion_to_environment}/.promotion-log.txt

                git add -Av
                git commit -m "Promotion from ${sourceEnv} to ${env_promotion_to_environment}"
                commitId=\$(git rev-parse --short=8 HEAD)
                dateTime=\$(git show -s --format=%cd --date=format:%Y-%m-%d_%H-%M-%S \$commitId)
                commitTag="Promote_tar-surge-client_${sourceEnv}_to_${env_promotion_to_environment}_\${commitId}_\$dateTime"
                echo "Tagging with: \$commitTag"
                git tag -f -a "\$commitTag" -m "Promotion tag" "\$commitId"
                git push https://${NUSER}:${NPASS}@github.com/ca-mmis/deployments-combined-devops.git
                git push https://${NUSER}:${NPASS}@github.com/ca-mmis/deployments-combined-devops.git "\$commitTag"
              """
            }
          }
        }
      }
    }
  }
}

stage("Deploy") {
  steps {
    container(name: "aws-boto3") {
      script {
        lock(resource: 'tar-surge-client-deployment', inversePrecedence: false) {
          dir("${WORKSPACE}/deploytarget") {
            withCredentials([usernamePassword(credentialsId: "github-key", usernameVariable: 'NUSER', passwordVariable: 'NPASS')]) {
              sh """
                echo "Cloning tar-surge-client-deployment..."
                git clone https://${NUSER}:${NPASS}@github.com/ca-mmis/tar-surge-client-deployment.git
                cd tar-surge-client-deployment
                git config --global user.email "jenkins@cammis.com"
                git config --global user.name "jenkins"
                git checkout master
                git pull

                ZIP_NAME=thickclient-${env_promotion_to_environment}.zip
                rm -f tar-surge-client/\$ZIP_NAME
                mkdir -p tmpdir/Thickclient

                echo "Copying artifacts from deployments-combined-devops/${env_promotion_to_environment}/Thickclient..."
                cp -a ${WORKSPACE}/deployrepo/deployments-combined-devops/tar-surge-client/${env_promotion_to_environment}/Thickclient/* tmpdir/Thickclient/

                echo "Creating ZIP: \$ZIP_NAME"
                cd tmpdir
                zip -r ../tar-surge-client/\$ZIP_NAME Thickclient
                cd ..
                rm -rf tmpdir

                git add tar-surge-client/\$ZIP_NAME
                git commit -m "Deploy \$ZIP_NAME from ${env_promotion_to_environment} promotion"
                git push https://${NUSER}:${NPASS}@github.com/ca-mmis/tar-surge-client-deployment.git

                commitId=\$(git rev-parse --short=8 HEAD)
                dateTime=\$(git show -s --format=%cd --date=format:%Y-%m-%d_%H-%M-%S \$commitId)
                commitTag="Deployed_to_${env_promotion_to_environment}_\${commitId}_\$dateTime"
                echo "Tagging with: \$commitTag"
                git tag -f -a "\$commitTag" -m "Deployment tag for \$ZIP_NAME" "\$commitId"
                git push https://${NUSER}:${NPASS}@github.com/ca-mmis/tar-surge-client-deployment.git
                git push https://${NUSER}:${NPASS}@github.com/ca-mmis/tar-surge-client-deployment.git "\$commitTag"
              """
            }
          }
        }
      }
    }
  }
}
