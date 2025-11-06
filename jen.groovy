/*
=======================================================================================
This file is being updated constantly by the DevOps team to introduce new enhancements
based on the template.  If you have suggestions for improvement,
please contact the DevOps team so that we can incorporate the changes into the
template.  In the meantime, if you have made changes here or don't want this file to be
updated, please indicate so at the beginning of this file.
=======================================================================================
*/

def branch = env.BRANCH_NAME ?: "master"
def workingDir = "/home/jenkins/agent"

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
    timeout(time:5 , unit: 'HOURS')
    skipDefaultCheckout()
    buildDiscarder(logRotator(numToKeepStr: '20'))
  }

  environment {
    env_promotion_to_environment=""
  }

  stages {

    stage("Initialize") {
      steps {
        container(name: "node") {
          script {
            properties([
              parameters([
                choice(name: 'PROMOTE_TO_ENV', choices: ['sit', 'uat', 'prd'], description: 'Select promotion target environment')
              ])
            ])
            env_promotion_to_environment = params.PROMOTE_TO_ENV
            deleteDir()
            checkout(scm)
            echo "Selected environment for promotion: ${env_promotion_to_environment}"
          }
        }
      }
    }

    stage("Prepare Deployment") {
      steps {
        container(name: "aws-boto3") {
          script {
            lock(resource: 'deployments-github-repo', inversePrecedence: false) {
              dir("${WORKSPACE}/deployrepo") {
                withCredentials([usernamePassword(credentialsId: "github-key", usernameVariable: 'NUSER', passwordVariable: 'NPASS')]) {

                  // Determine the source environment dynamically
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
  }

  post {
    always {
      echo "Build Process complete."
    }
    success {
      echo "Promotion and Deployment Successful!"
    }
    unstable {
      echo "Build unstable — please check logs."
    }
    aborted {
      echo "Pipeline aborted by user."
    }
    failure {
      echo "Build failed during promotion or deployment."
    }
  }
}
