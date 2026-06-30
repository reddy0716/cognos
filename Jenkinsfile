stage('Prepare Deployment') {
      steps {
        container(name: "jnlp") {
          script {
            lock(resource: 'deployments-github-repo', inversePrecedence: false) {
              dir("${WORKSPACE}/deployrepo") {
                withCredentials([usernamePassword(credentialsId: "github-key", usernameVariable: 'NUSER', passwordVariable: 'NPASS')]) {

                  def rollbackRef = params.ROLLBACK_REF?.trim()

                  sh """
                    pwd
                    git clone https://${NUSER}:${NPASS}@github.com/ca-mmis/deployments-combined-devops.git --depth=1
                    git config --global user.email "jenkins@cammis.com"
                    git config --global user.name "jenkins"
                    cd deployments-combined-devops
                    git checkout master
                    git pull

                    ROLLBACK_REF="${rollbackRef}"

                    if [ -n "\$ROLLBACK_REF" ]; then
                      echo "Rollback requested — restoring BatchJobs/${env_promotion_to_environment} from ref: \$ROLLBACK_REF"

                      # depth=1 clone won't have older history, so fetch the specific ref
                      git fetch --depth=1 origin "\$ROLLBACK_REF"

                      rm -rf BatchJobs/${env_promotion_to_environment}
                      mkdir -p BatchJobs/${env_promotion_to_environment}
                      git checkout "\$ROLLBACK_REF" -- BatchJobs/${env_promotion_to_environment}

                      git add -A BatchJobs/${env_promotion_to_environment}

                      if ! git diff-index --quiet HEAD; then
                        git commit -m "Rollback BatchJobs/${env_promotion_to_environment} to \$ROLLBACK_REF"
                        commitId=\""\$(git rev-parse --short=8 HEAD)"\"
                        echo "The commit ID is: \$commitId"
                        dateTime=\""\$(git show -s --format=%cd --date=format:%Y-%m-%d_%H-%M-%S \$commitId)"\"
                        commitTag="Rollback_BatchJobs_${env_promotion_to_environment}_\${commitId}_\$dateTime"
                        echo "The rollback commit tag will be: \$commitTag"
                        git tag -f -a \"\$commitTag\" -m "rollback tag" \"\$commitId\"
                        git push https://${NUSER}:${NPASS}@github.com/ca-mmis/deployments-combined-devops.git
                        git push https://${NUSER}:${NPASS}@github.com/ca-mmis/deployments-combined-devops.git "\$commitTag"
                      else
                        echo "Nothing changed — already at requested rollback state."
                      fi

                    else
                      mkdir -p BatchJobs/${env_promotion_to_environment}
                      touch BatchJobs/${env_promotion_to_environment}/tempfile
                      rm -r BatchJobs/${env_promotion_to_environment}/*
                      cp -a BatchJobs/${env_promotion_from_environment}/. BatchJobs/${env_promotion_to_environment}/
                      git add -A BatchJobs/${env_promotion_to_environment}

                      if ! git diff-index --quiet HEAD; then
                        git commit -m "Promotion of BatchJObs from ${env_promotion_from_environment} to ${env_promotion_to_environment}"
                        commitId=\""\$(git rev-parse --short=8 HEAD)"\"
                        echo "The commit ID is: \$commitId"
                        dateTime=\""\$(git show -s --format=%cd --date=format:%Y-%m-%d_%H-%M-%S \$commitId)"\"
                        commitTag="Promote_BatchJobs_to_${env_promotion_to_environment}_\${commitId}_\$dateTime"
                        echo "The commit tag will be: \$commitTag"
                        git tag -f -a \"\$commitTag\" -m "tag promotion" \"\$commitId\"
                        git push https://${NUSER}:${NPASS}@github.com/ca-mmis/deployments-combined-devops.git
                        git push https://${NUSER}:${NPASS}@github.com/ca-mmis/deployments-combined-devops.git "\$commitTag"
                      else
                        echo "Nothing changes to commit to deployment repository, still will deploy..."
                      fi
                    fi
                  """
                } //end withCredentials
              } //end dir
            } //end lock
          } // end of script
        } // end of container
      } // end of steps
    }  // end of Prepare Deployment Stage
