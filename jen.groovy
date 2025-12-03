/*
=======================================================================================
This file is maintained by the DevOps team. 
For enhancement requests, contact DevOps.
If you do NOT want this pipeline to be auto-updated, note so at the top.
=======================================================================================
*/

def branch = env.BRANCH_NAME ?: "master"
def workingDir = "/home/jenkins/agent"

def DEPLOY_FROM_ENV = [
  "dev":"N/A",
  "sit":"dev",
  "uat":"sit",
  "prd":"uat"
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
            hostPath: { path: /var/run/docker.sock }
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
            workingDir: ${workingDir}
            env: 
              - { name: HOME, value: ${workingDir} }
              - { name: BRANCH, value: ${branch} }

          - name: node
            image: registry.access.redhat.com/ubi8/nodejs-18:latest
            tty: true
            command: ["/bin/bash"]
            workingDir: ${workingDir}
            env:
              - { name: HOME, value: ${workingDir} }
              - { name: BRANCH, value: ${branch} }

          - name: aws-boto3
            image: 136299550619.dkr.ecr.us-west-2.amazonaws.com/cammisboto3:1.0.1
            tty: true
            command: ["/bin/bash"]
            workingDir: ${workingDir}
            env:
              - { name: HOME, value: ${workingDir} }
              - { name: BRANCH, value: ${branch} }

          - name: jnlp
            image: jenkins/inbound-agent
            env:
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
    timeout(time: 3, unit: "HOURS")
    skipDefaultCheckout()
    buildDiscarder(logRotator(numToKeepStr: "20"))
  }

  environment {
    env_promotion_to_environment = ""
    env_promotion_from_environment = ""
  }

  stages {
    stage("Initialize") {
      steps {
        container("node") {
          script {
            properties([
              parameters([
                choice(name: "PROMOTE_TO_ENV", choices: ["sit","uat","prd"], 
                       description: "Environment to Promote TO")
              ])
            ])

            env_promotion_to_environment = params.PROMOTE_TO_ENV
            env_promotion_from_environment = DEPLOY_FROM_ENV["${env_promotion_to_environment}"]

            deleteDir()
            checkout(scm).GIT_COMMIT

            echo "YES → PROMOTING TO: ${env_promotion_to_environment.toUpperCase()}"
            echo "FROM LOWER ENV: ${env_promotion_from_environment.toUpperCase()}"
          }
        }
      }
    }

    stage("Build Promotion Package") {
      steps {
        container("dotnet") {
          script {
            lock(resource: 'deployments-github-repo') {

              dir("${WORKSPACE}/deployrepo") {

                withCredentials([usernamePassword(credentialsId: "github-key",
                                                  usernameVariable: 'NUSER',
                                                  passwordVariable: 'NPASS')]) {

                  sh """
                    git clone https://${NUSER}:${NPASS}@github.com/ca-mmis/tar-surge-client.git --depth=1
                    git clone https://${NUSER}:${NPASS}@github.com/ca-mmis/deployments-combined-devops.git --depth=1

                    git config --global user.email "jenkins@cammis.com"
                    git config --global user.name "jenkins"

                    cd tar-surge-client
                    mkdir -p devops/codedeploy/SurgeUpdate
                    cd ..

                    cd deployments-combined-devops
                    git checkout master
                    git pull

                    echo "Extracting ZIP from LOWER ENV: ${env_promotion_from_environment.toUpperCase()}"
                    unzip -o SurgeAutoupdate/${env_promotion_from_environment}/SurgeUpdate/SurgeUpdate_${env_promotion_from_environment.toUpperCase()}.ZIP \
                      -d ${WORKSPACE}/deployrepo/tar-surge-client/devops/codedeploy/SurgeUpdate

                    echo "Overlay configs for ${env_promotion_to_environment.toUpperCase()}"
                    cp ${WORKSPACE}/deployrepo/tar-surge-client/Config/${env_promotion_to_environment.toUpperCase()}/* \
                       ${WORKSPACE}/deployrepo/tar-surge-client/devops/codedeploy/SurgeUpdate

                    cd ..
                    cd tar-surge-client

                    echo "Removing old SurgeInstall BAT"
                    rm -f devops/codedeploy/SurgeUpdate/SurgeInstall_${env_promotion_from_environment.toUpperCase()}.bat || true

                    echo "Creating new ZIP"
                    rm -f devops/codedeploy/SurgeUpdate_${env_promotion_to_environment.toUpperCase()}.ZIP || true

                    cd devops/codedeploy
                    zip -r SurgeUpdate_${env_promotion_to_environment.toUpperCase()}.ZIP SurgeUpdate

                    echo "Preparing ${env_promotion_to_environment.toUpperCase()} directory inside deployments-combined-devops..."

                    cd ${WORKSPACE}/deployrepo/deployments-combined-devops
                    
                    # Create target env directory
                    mkdir -p SurgeAutoupdate/${env_promotion_to_environment}/SurgeUpdate
                    
                    # Clean old files
                    rm -rf SurgeAutoupdate/${env_promotion_to_environment}/SurgeUpdate/*
                    
                    # Copy newly created ZIP
                    cp ${WORKSPACE}/deployrepo/tar-surge-client/devops/codedeploy/SurgeUpdate_${env_promotion_to_environment.toUpperCase()}.ZIP \
                       SurgeAutoupdate/${env_promotion_to_environment}/SurgeUpdate/
                    
                    echo "Updated SurgeAutoupdate/${env_promotion_to_environment}/SurgeUpdate/ with new ZIP"


                    echo "Build/ZIP Promotion package is ready"
                  """
                }
              }
            }
          }
        }
      }
    }

    stage("Deploy with CodeDeploy") {
      steps {
        container("aws-boto3") {
          script {

            SURGE_ENV = env_promotion_to_environment.toUpperCase()
            echo "Deploying SURGE_ENV = ${SURGE_ENV}"

            /******** Non-DR ********/
            withCredentials([aws(credentialsId: 'jenkins-ecr', accessKeyVariable: 'AWS_ACCESS_KEY_ID', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY')]) {
              step([$class: 'AWSCodeDeployPublisher',
                applicationName: "tar-surge-app-${SURGE_ENV}",
                awsAccessKey: "${AWS_ACCESS_KEY_ID}",
                awsSecretKey: "${AWS_SECRET_ACCESS_KEY}",
                credentials: "awsAccessKey",
                deploymentConfig: "tar-surge-app-${SURGE_ENV}-config",
                deploymentGroupName: "tar-surge-app-${SURGE_ENV}-INPLACE-deployment-group",
                deploymentMethod: "deploy",
                region: "us-west-2",
                s3bucket: "dhcs-codedeploy-app",
                subdirectory: "tar-surge-client/devops/codedeploy",
                includes: "**",
                waitForCompletion: true
              ])
            }

            /******** DR ********/
            withCredentials([aws(credentialsId: 'jenkins-ecr', accessKeyVariable: 'AWS_ACCESS_KEY_ID', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY')]) {
              step([$class: 'AWSCodeDeployPublisher',
                applicationName: "tar-surge-app-${SURGE_ENV}-DR",
                awsAccessKey: "${AWS_ACCESS_KEY_ID}",
                awsSecretKey: "${AWS_SECRET_ACCESS_KEY}",
                credentials: "awsAccessKey",
                deploymentConfig: "tar-surge-app-${SURGE_ENV}-DR-config",
                deploymentGroupName: "tar-surge-app-${SURGE_ENV}-DR-INPLACE-deployment-group",
                deploymentMethod: "deploy",
                region: "us-east-1",
                s3bucket: "dhcs-codedeploy-app-dr",
                subdirectory: "tar-surge-client/devops/codedeploy",
                includes: "**",
                waitForCompletion: true
              ])
            }
          }
        }
      }
    }


    /***********************
     * PUSH TO DEPLOYMENT REPO
     ***********************/
    stage("Push Artifacts to Deployment Repo") {
      steps {
        container("dotnet") {
          script {
            lock(resource: 'deployments-github-repo') {

              dir("${WORKSPACE}/deployrepo") {
                withCredentials([usernamePassword(credentialsId: "github-key",
                                                  usernameVariable: 'NUSER',
                                                  passwordVariable: 'NPASS')]) {

                  sh """
                    git clone https://${NUSER}:${NPASS}@github.com/ca-mmis/tar-surge-client-deployment.git --depth=1
                    cd tar-surge-client-deployment
                    git checkout master
                    git pull

                    echo "Copy ZIP and BAT"
                    cp ${WORKSPACE}/deployrepo/tar-surge-client/devops/codedeploy/SurgeUpdate_${env_promotion_to_environment.toUpperCase()}.ZIP tar-surge-client/SurgeAutoupdate
                    cp ${WORKSPACE}/deployrepo/tar-surge-client/Config/${env_promotion_to_environment.toUpperCase()}/SurgeInstall_${env_promotion_to_environment.toLowerCase()}.bat tar-surge-client/SurgeAutoupdate

                    if [[ -n \$(git status --porcelain) ]]; then
                      git add .
                      git commit -m "Automated SurgeUpdate deployment for ${env_promotion_to_environment.toUpperCase()}"
                      git push origin master
                    fi

                    git tag -f -a "SURGE-${env_promotion_to_environment.toUpperCase()}" -m "Deploying Thickclient ${env_promotion_to_environment.toUpperCase()}"
                    git push origin "SURGE-${env_promotion_to_environment.toUpperCase()}" --force
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
    always { echo "Pipeline completed." }
    success { echo "SUCCESS Promotion Completed Successfully" }
    failure { echo "FAILED – Check logs" }
  }
}
