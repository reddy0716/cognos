sh """
              # Clone tar-surge-client-deployment repo
              git clone https://${NUSER}:${NPASS}@github.com/ca-mmis/tar-surge-client-deployment.git --depth=1
              cd tar-surge-client-deployment
              git checkout master
              git pull

              # Create folder if not present
              mkdir -p ${WORKSPACE}/deployrepo/tar-surge-client-deployment/tar-surge-client/SurgeAutoupdate

              # Copy artifacts from Jenkins build output
              cp ${WORKSPACE}/devops/codedeploy/SurgeUpdate_DEV.ZIP tar-surge-client/SurgeAutoupdate
              cp ${WORKSPACE}/devops/codedeploy/SurgeUpdate/Version.TXT tar-surge-client/SurgeAutoupdate

              # Commit and push changes
              if [[ -n \$(git status --porcelain) ]]; then
                git add .
                git commit -m "Automated commit - Deploying SurgeUpdate artifacts"
                git push origin master
              fi

              # Tag this deployment
              git tag -f -a "${env_tag_name}" -m "Deploying Thickclient - Tag ${env_tag_name}"
              git push origin "${env_tag_name}" --force
            """
sh """
  # Clone deployments-combined-devops repo
  git clone https://${NUSER}:${NPASS}@github.com/ca-mmis/deployments-combined-devops.git --depth=1
  cd deployments-combined-devops
  git checkout master
  git pull

  # Prepare folders
  mkdir -p ${WORKSPACE}/deployrepo/deployments-combined-devops/SurgeAutoupdate/dev/SurgeUpdate
  rm -rf SurgeAutoupdate/dev/SurgeUpdate/*

  # Copy new build artifacts
  cp -a ${WORKSPACE}/devops/codedeploy/SurgeUpdate/. SurgeAutoupdate/dev/SurgeUpdate/

  # Commit and push
  git add .
  git commit -m "Updated build artifacts for tar-surge-client build ${env_tag_name}" || true
  git push https://${NUSER}:${NPASS}@github.com/ca-mmis/deployments-combined-devops.git
"""
