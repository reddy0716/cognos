/*
=======================================================================================
This file is being updated constantly by the DevOps team to introduce new enhancements
based on the template.  If you have suggestions for improvement,
please contact the DevOps team so that we can incorporate the changes into the
template.  In the meantime, if you have made changes here or don't want this file to be
updated, please indicate so at the beginning of this file.
=======================================================================================
*/

def branch = env.BRANCH_NAME ?: "sandbox00"
def workingDir = "/home/jenkins/agent"

def ENV_ALIAS_MAP = [
  "hotfix": "SIT",
  "prd"   : "PRD"
]

def DEPLOY_FROM_ENV = [
  "sandbox": "N/A",
  "hotfix" : "sandbox",
  "prd"    : "hotfix"
]

def SURGE_ENV

pipeline {
  agent {
    kubernetes {
      yaml """
        apiVersion: v1
        kind: Pod
        spec:
          serviceAccountName: jenkins
          volumes:
            - name: dockersock
              hostPath:
                path: /var/run/docker.sock
            - emptyDir: {}
              name: varlibcontainers
            - name: jenkins-trusted-ca-bundle
              configMap:
                name: jenkins-trusted-ca-bundle
                defaultMode: 420
                optional: true
          containers:
            - name: dotnet
              image: 136299550619.dkr.ecr.us-west-2.amazonaws.com/cammismspapp:1.0.34
              tty: true
              command: ["/bin/bash"]
              securityContext:
                privileged: true
              workingDir: ${workingDir}
              envFrom:
                - configMapRef:
                    name: jenkins-agent-env
                    optional: true
              env:
                - name: HOME
                  value: ${workingDir}
                - name: BRANCH
                  value: ${branch}
            - name: jnlp
              securityContext:
                privileged: true
              envFrom:
                - configMapRef:
                    name: jenkins-agent-env
                    optional: true
              env:
                - name: GIT_SSL_CAINFO
                  value: "/etc/pki/tls/certs/ca-bundle.crt"
              volumeMounts:
                - name: jenkins-trusted-ca-bundle
                  mountPath: /etc/pki/tls/certs
            - name: node
              image: registry.access.redhat.com/ubi8/nodejs-18:latest
              tty: true
              command: ["/bin/bash"]
              securityContext:
                privileged: true
              workingDir: ${workingDir}
              envFrom:
                - configMapRef:
                    name: jenkins-agent-env
                    optional: true
              env:
                - name: HOME
                  value: ${workingDir}
                - name: BRANCH
                  value: ${branch}
                - name: GIT_SSL_CAINFO
                  value: "/etc/pki/tls/certs/ca-bundle.crt"
              volumeMounts:
                - name: jenkins-trusted-ca-bundle
                  mountPath: /etc/pki/tls/certs
            - name: aws-boto3
              image: 136299550619.dkr.ecr.us-west-2.amazonaws.com/cammisboto3:1.0.1
              tty: true
              command: ["/bin/bash"]
              workingDir: ${workingDir}
              envFrom:
                - configMapRef:
                    name: jenkins-agent-env
                    optional: true
              env:
                - name: HOME
                  value: ${workingDir}
                - name: BRANCH
                  value: ${branch}
                - name: GIT_SSL_CAINFO
                  value: "/etc/pki/tls/certs/ca-bundle.crt"
              volumeMounts:
                - name: jenkins-trusted-ca-bundle
                  mountPath: /etc/pki/tls/certs
      """
    }
  }

  options {
    timestamps()
    disableConcurrentBuilds()
    timeout(time: 5, unit: 'HOURS')
    skipDefaultCheckout()
    buildDiscarder(logRotator(numToKeepStr: '20'))
  }

  environment {
    env_current_git_commit = ""
    env_accesskey = ""
    env_secretkey = ""
    env_tag_name = ""
    env_deploy_env = ""
    env_promotion_to_environment = ""
    env_promotion_from_environment = ""
  }

  stages {
    stage("initialize") {
      steps {
        container(name: "node") {
          script {
            properties([
              parameters([
                choice(name: 'PROMOTE_TO_ENV', choices: ['hotfix', 'prd'], description: 'Where to promote to?'),
                string(name: 'ROLLBACK_REF', defaultValue: '', description: 'Optional deployment repo tag/commit to restore before deploy')
              ])
            ])

            env_promotion_to_environment = params.PROMOTE_TO_ENV
            env_promotion_from_environment = DEPLOY_FROM_ENV[env_promotion_to_environment]
            SURGE_ENV = ENV_ALIAS_MAP[env_promotion_to_environment]

            if (!SURGE_ENV) {
              error("Unable to resolve deployment environment for ${env_promotion_to_environment}")
            }

            if (!env_promotion_from_environment || env_promotion_from_environment == "N/A") {
              error("Invalid promotion path for target environment: ${env_promotion_to_environment}")
            }

            def rollbackRef = params.ROLLBACK_REF?.trim()

            echo "Promoting to environment: ${env_promotion_to_environment}"
            echo "Promoting from environment: ${env_promotion_from_environment}"
            echo "Resolved deploy environment: ${SURGE_ENV}"
            echo "Rollback ref: ${rollbackRef}"

            deleteDir()
            checkout(scm).GIT_COMMIT
          }
        }
      }
    }

    stage('Prepare Deployment') {
      steps {
        container(name: "aws-boto3") {
          script {
            container(name: "jnlp") {
              lock(resource: 'deployments-github-repo', inversePrecedence: false) {
                dir("${WORKSPACE}/deployrepo") {
                  withCredentials([usernamePassword(credentialsId: "github-key", usernameVariable: 'NUSER', passwordVariable: 'NPASS')]) {
                    script {
                      def rollbackRef = params.ROLLBACK_REF?.trim()

                      if (rollbackRef) {
                        sh """
                          set -e
                          pwd
                          git clone https://${NUSER}:${NPASS}@github.com/ca-mmis/deployments-combined-devops.git --depth=50
                          git config --global user.email "jenkins@cammis.com"
                          git config --global user.name "jenkins"

                          cd deployments-combined-devops
                          git checkout master
                          git pull

                          echo "Rollback requested for ${env_promotion_to_environment} using ref: ${rollbackRef}"

                          # Remove current target folder completely
                          rm -rf tar-surge-app/${env_promotion_to_environment}
                          mkdir -p tar-surge-app/${env_promotion_to_environment}

                          # Restore target folder from specific ref
                          git checkout ${rollbackRef} -- tar-surge-app/${env_promotion_to_environment}

                          git add -Av

                          if ! git diff-index --quiet HEAD; then
                            git commit -m "Rollback tar-surge-app ${env_promotion_to_environment} to ${rollbackRef}"
                            commitId="\$(git rev-parse --short=8 HEAD)"
                            dateTime="\$(git show -s --format=%cd --date=format:%Y-%m-%d_%H-%M-%S \$commitId)"
                            commitTag="Rollback_tar-surge-app_${env_promotion_to_environment}_\${commitId}_\$dateTime"
                            echo "Rollback tag will be: \$commitTag"
                            git tag -f -a "\$commitTag" -m "rollback tag" "\$commitId"
                            git push https://${NUSER}:${NPASS}@github.com/ca-mmis/deployments-combined-devops.git
                            git push https://${NUSER}:${NPASS}@github.com/ca-mmis/deployments-combined-devops.git "\$commitTag"
                          else
                            echo "No rollback changes detected, still will deploy..."
                          fi
                        """
                      } else {
                        sh """
                          set -e
                          pwd
                          git clone https://${NUSER}:${NPASS}@github.com/ca-mmis/deployments-combined-devops.git --depth=1
                          git config --global user.email "jenkins@cammis.com"
                          git config --global user.name "jenkins"

                          cd deployments-combined-devops
                          git checkout master
                          git pull

                          mkdir -p tar-surge-app/${env_promotion_to_environment}
                          touch tar-surge-app/${env_promotion_to_environment}/tempfile
                          rm -rf tar-surge-app/${env_promotion_to_environment}/*
                          cp -a tar-surge-app/${env_promotion_from_environment}/. tar-surge-app/${env_promotion_to_environment}/
                          git add -Av

                          if ! git diff-index --quiet HEAD; then
                            git commit -m "Promotion of tar-surge-app from ${env_promotion_from_environment} to ${env_promotion_to_environment}"
                            commitId="\$(git rev-parse --short=8 HEAD)"
                            echo "The commit ID is: \$commitId"
                            dateTime="\$(git show -s --format=%cd --date=format:%Y-%m-%d_%H-%M-%S \$commitId)"
                            commitTag="Promote_tar-surge-app_to_${env_promotion_to_environment}_\${commitId}_\$dateTime"
                            echo "The commit tag will be: \$commitTag"
                            git tag -f -a "\$commitTag" -m "tag promotion" "\$commitId"
                            git push https://${NUSER}:${NPASS}@github.com/ca-mmis/deployments-combined-devops.git
                            git push https://${NUSER}:${NPASS}@github.com/ca-mmis/deployments-combined-devops.git "\$commitTag"
                          else
                            echo "Nothing changes to commit to deployment repository, still will deploy..."
                          fi
                        """
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
    
  stage('Deploy') {
      steps {
        container(name: "aws-boto3") {
          script {
            echo "Deploy Using AWS CodeDeploy"

            // Need to copy files from env_promotion_to_environment back into the devops/codedeploy directory to then deploy

            sh """
              cp -a ${WORKSPACE}/deployrepo/deployments-combined-devops/tar-surge-app/${env_promotion_to_environment}/. ${WORKSPACE}/devops/codedeploy/
              sed -i "s,{DEPLOY_FILES},true," ${WORKSPACE}/devops/codedeploy/after-install.bat
            """
            SURGE_ENV = ENV_ALIAS_MAP[env_promotion_to_environment]

            echo "Here is the environment to go to: ${SURGE_ENV}"

            echo "Deploying to Non-DR"

            withCredentials([aws(accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: 'jenkins-ecr', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY')]) {
              step([$class: 'AWSCodeDeployPublisher',
                  applicationName: "tar-surge-app-${SURGE_ENV}",
                  awsAccessKey: "${AWS_ACCESS_KEY_ID}",
                  awsSecretKey: "${AWS_SECRET_ACCESS_KEY}",
                  credentials: 'awsAccessKey',
                  deploymentConfig: "tar-surge-app-${SURGE_ENV}-config",
                  deploymentGroupAppspec: false,
                  deploymentGroupName: "tar-surge-app-${SURGE_ENV}-INPLACE-deployment-group",
                  deploymentMethod: 'deploy',
                  excludes: '', iamRoleArn: '', includes: '**', pollingFreqSec: 15, pollingTimeoutSec: 900, proxyHost: '', proxyPort: 0,
                  region: 'us-west-2', s3bucket: 'dhcs-codedeploy-app', 
                  subdirectory: 'devops/codedeploy', versionFileName: '', waitForCompletion: true])
            }

            echo "Deploying to DR"

            withCredentials([aws(accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: 'jenkins-ecr', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY')]) {
              step([$class: 'AWSCodeDeployPublisher',
                  applicationName: "tar-surge-app-${SURGE_ENV}-DR",
                  awsAccessKey: "${AWS_ACCESS_KEY_ID}",
                  awsSecretKey: "${AWS_SECRET_ACCESS_KEY}",
                  credentials: 'awsAccessKey',
                  deploymentConfig: "tar-surge-app-${SURGE_ENV}-DR-config",
                  deploymentGroupAppspec: false,
                  deploymentGroupName: "tar-surge-app-${SURGE_ENV}-DR-INPLACE-deployment-group",
                  deploymentMethod: 'deploy',
                  excludes: '', iamRoleArn: '', includes: '**', pollingFreqSec: 15, pollingTimeoutSec: 900, proxyHost: '', proxyPort: 0,
                  region: 'us-east-1', s3bucket: 'dhcs-codedeploy-app-dr', 
                  subdirectory: 'devops/codedeploy', versionFileName: '', waitForCompletion: true])
            }
          } // end of script
        } // end of container
      } // end of steps
    } // end of Deploy stage
  }

  post {
    always {
      echo "Build Process complete."
    }
    success {
      echo "Build Process was success."
    }
    unstable {
      echo "Build is unstable."
    }
    aborted {
      echo "Pipeline aborted."
    }
    failure {
      echo "Build encountered failures."
    }
    changed {
      echo "Build content was changed."
    }
  }
}
