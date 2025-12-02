 stage("Prepare Promotion Package") {
      steps {
        container("node") {
          script {
            sh """
              set -e
              echo "Preparing promotion package..."
              git clone https://github.com/ca-mmis/deployments-combined-devops.git
              cd deployments-combined-devops
              git checkout master
              git pull

              mkdir -p ../devops/codedeploy/SurgeUpdate

              echo "Extracting source package from ${env_promotion_from_environment.toUpperCase()}..."
              unzip -o tar-surge-client/${env_promotion_from_environment}/SurgeUpdate_${env_promotion_from_environment.toUpperCase()}.ZIP -d tmp/SurgeUpdate

              echo "Overlaying configs for ${env_promotion_to_environment.toUpperCase()}..."
              cp ../tar-surge-client/config/${env_promotion_to_environment.toUpperCase()}/* tmp/SurgeUpdate/

              echo "Repackaging as SurgeUpdate_${env_promotion_to_environment.toUpperCase()}.ZIP"
              cd tmp
              zip -r ../devops/codedeploy/SurgeUpdate/SurgeUpdate_${env_promotion_to_environment.toUpperCase()}.ZIP SurgeUpdate
              cd ..
              cp tar-surge-client/${env_promotion_from_environment}/Version.TXT ../devops/codedeploy/SurgeUpdate/

              echo "Promotion build complete."
            """
          }
        }
      }
    }

    stage("Update Deployment Repositories") {
      steps {
        container("aws-boto3") {
          script {
            lock(resource: 'deployments-github-repo', inversePrecedence: false) {
              dir("${WORKSPACE}/deploytarget") {
                withCredentials([usernamePassword(credentialsId: "github-key", usernameVariable: 'NUSER', passwordVariable: 'NPASS')]) {

                  // ---- Push to tar-surge-client-deployment ----
                  sh """
                    set -e
                    echo "Updating tar-surge-client-deployment repo..."
                    git clone https://${NUSER}:${NPASS}@github.com/ca-mmis/tar-surge-client-deployment.git
                    cd tar-surge-client-deployment
                    git config --global user.email "jenkins@cammis.com"
                    git config --global user.name "jenkins"
                    git checkout master
                    git pull

                    ZIP_NAME=SurgeUpdate_${env_promotion_to_environment.toUpperCase()}.ZIP
                    echo "Copying new package..."
                    rm -f tar-surge-client/\$ZIP_NAME
                    cp ${WORKSPACE}/devops/codedeploy/SurgeUpdate/\$ZIP_NAME tar-surge-client/
                    cp ${WORKSPACE}/devops/codedeploy/SurgeUpdate/Version.TXT tar-surge-client/

                    git add tar-surge-client/
                    git commit -m "Promoted SURGE Client from ${env_promotion_from_environment} to ${env_promotion_to_environment}" || true
                    git push origin master

                    commitId=\$(git rev-parse --short=8 HEAD)
                    dateTime=\$(git show -s --format=%cd --date=format:%Y-%m-%d_%H-%M-%S \$commitId)
                    commitTag="Promote_SurgeUpdate_to_${env_promotion_to_environment}_\${commitId}_\$dateTime"
                    git tag -f -a "\$commitTag" -m "Promotion tag for \$ZIP_NAME" "\$commitId"
                    git push origin master --tags
                  """

                  // ---- Push to deployments-combined-devops ----
                  sh """
                    echo "Updating deployments-combined-devops repo..."
                    git clone https://${NUSER}:${NPASS}@github.com/ca-mmis/deployments-combined-devops.git
                    cd deployments-combined-devops
                    git checkout master
                    git pull

                    mkdir -p tar-surge-client/${env_promotion_to_environment}
                    rm -rf tar-surge-client/${env_promotion_to_environment}/*
                    cp -a ${WORKSPACE}/devops/codedeploy/SurgeUpdate/. tar-surge-client/${env_promotion_to_environment}/

                    git add .
                    git commit -m "Promoted SURGE Client from ${env_promotion_from_environment} to ${env_promotion_to_environment}" || true
                    git push origin master
                  """
                }
              }
            }
          }
        }
      }
    }
