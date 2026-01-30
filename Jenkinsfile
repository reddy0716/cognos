/*
=======================================================================================
 ENV PIPELINE

 User-facing labels:
   SBX -> DEV server
   HFX -> SIT server

 TARGET_ENV  : Used for CodeDeploy / Vault / RPM resolution
 DISPLAY_ENV : Logical label (SANDBOX / HOTFIX)
=======================================================================================
*/

def branch = env.BRANCH_NAME ?: "master"
def workingDir = "/home/jenkins/agent"

// Resolved variables
def TARGET_ENV  = ""
def DISPLAY_ENV = ""

def VAULT_SECRET_PATH = [
  "DEV":"kv-dev/data/us-west/dev-tar/tar-surgenet-service-secrets",
  "SIT":"kv-tst/data/us-west/sit-tar/tar-surgenet-service-secrets"
]

def VAULT_SECRET_PATH_LTAR = [
  "DEV":"kv-dev/data/us-west/dev-tar/tar-ltar-service-secrets",
  "SIT":"kv-tst/data/us-west/sit-tar/tar-ltar-service-secrets"
]

def VAULT_SECRET_PATH_IMGVWR = [
  "DEV":"kv-dev/data/us-west/dev-tar/tar-image-viewer-service-secrets",
  "SIT":"kv-tst/data/us-west/sit-tar/tar-image-viewer-service-secrets"
]

def SURGE_ENV_CONFIG = [
  "DEV": ["SURGE_ENVNAME":"DEV", "SURGE_RPM_ROOT":"D:/inetpub/ApiServices/RPM/dhcs_dev/rpm_root"],
  "SIT": ["SURGE_ENVNAME":"SIT", "SURGE_RPM_ROOT":"D:/inetpub/ApiServices/RPM/dhcs_sit/rpm_root"]
]

def VAULT_ADDR = [
  "DEV":"https://np.secrets.cammis.medi-cal.ca.gov/v1/",
  "SIT":"https://np.secrets.cammis.medi-cal.ca.gov/v1/"
]

def VAULT_APPROLE_AUTH_PATH = "auth/approle/login"

pipeline {
  agent {
    kubernetes {
      yaml """
apiVersion: v1
kind: Pod
spec:
  serviceAccountName: jenkins
  containers:
    - name: aws-boto3
      image: 136299550619.dkr.ecr.us-west-2.amazonaws.com/cammisboto3:1.0.1
      tty: true
      command: ["/bin/bash"]
      workingDir: ${workingDir}
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
    env_DEPLOY_ENVIRONMENT = "true"
    env_DEPLOY_FILES       = "false"
    env_DEPLOY_CONFIG      = "false"
  }

  stages {

    /* =========================
       INITIALIZE
       ========================= */
    stage("Initialize") {
      steps {
        container("aws-boto3") {
          script {

            properties([
              parameters([
                choice(
                  name: 'DEPLOY_ENV',
                  choices: ['NONE','SBX','HFX'],
                  description: 'Deployment Environment'
                )
              ])
            ])

            def ENV_ALIAS_MAP = [
              "SBX": [TARGET:"DEV", DISPLAY:"SANDBOX"],
              "HFX": [TARGET:"SIT", DISPLAY:"HOTFIX"]
            ]

            if (params.DEPLOY_ENV == "NONE") {
              TARGET_ENV  = "NONE"
              DISPLAY_ENV = "NONE"
            } else {
              TARGET_ENV  = ENV_ALIAS_MAP[params.DEPLOY_ENV].TARGET
              DISPLAY_ENV = ENV_ALIAS_MAP[params.DEPLOY_ENV].DISPLAY
            }

            echo "User selected label   : ${params.DEPLOY_ENV}"
            echo "Resolved TARGET_ENV   : ${TARGET_ENV}"
            echo "Resolved DISPLAY_ENV  : ${DISPLAY_ENV}"

            deleteDir()
            checkout(scm)
          }
        }
      }
    }

    /* =========================
       PREPARE DEPLOYMENT
       ========================= */
    stage("Prepare Deployment") {
      when {
        expression { TARGET_ENV != "NONE" }
      }
      steps {
        container("aws-boto3") {
          script {

            def surgeEnv = SURGE_ENV_CONFIG[TARGET_ENV]

            sh """
              echo "Preparing CodeDeploy structure"
              mkdir -p devops/codedeploy/surgeapi
              touch devops/codedeploy/surgeapi/placeholder.txt

              echo "Injecting Vault configuration"
              sed -i "s,{VAULT_ADDR},${VAULT_ADDR[TARGET_ENV]}," devops/codedeploy/environment/deploy-environment.ps1
              sed -i "s,{VAULT_SECRET_PATH},${VAULT_SECRET_PATH[TARGET_ENV]}," devops/codedeploy/environment/deploy-environment.ps1
              sed -i "s,{VAULT_SECRET_PATH_LTAR},${VAULT_SECRET_PATH_LTAR[TARGET_ENV]}," devops/codedeploy/environment/deploy-environment.ps1
              sed -i "s,{VAULT_SECRET_PATH_IMGVWR},${VAULT_SECRET_PATH_IMGVWR[TARGET_ENV]}," devops/codedeploy/environment/deploy-environment.ps1
              sed -i "s,{VAULT_APPROLE_AUTH_PATH},${VAULT_APPROLE_AUTH_PATH}," devops/codedeploy/environment/deploy-environment.ps1

              echo "Injecting SURGE values"
              sed -i "s,{SURGE_ENVNAME},${surgeEnv.SURGE_ENVNAME}," devops/codedeploy/environment/deploy-environment.ps1
              sed -i "s,{SURGE_RPM_ROOT},${surgeEnv.SURGE_RPM_ROOT}," devops/codedeploy/environment/deploy-environment.ps1

              echo "Enable ENV deployment mode"
              sed -i "s,{DEPLOY_ENVIRONMENT},${env_DEPLOY_ENVIRONMENT}," devops/codedeploy/after-install.bat
            """

            withCredentials([
              string(credentialsId: 'APPROLE_ROLE_ID',   variable: 'APPROLE_ROLE_ID'),
              string(credentialsId: 'APPROLE_SECRET_ID', variable: 'APPROLE_SECRET_ID')
            ]) {
              sh """
                sed -i "s,{APPROLE_ROLE_ID},${APPROLE_ROLE_ID}," devops/codedeploy/environment/deploy-environment.ps1
                sed -i "s,{APPROLE_SECRET_ID},${APPROLE_SECRET_ID}," devops/codedeploy/environment/deploy-environment.ps1
              """
            }
          }
        }
      }
    }

    /* =========================
       DEPLOY
       ========================= */
    stage("Deploy") {
      when {
        expression { TARGET_ENV != "NONE" }
      }
      steps {
        container("aws-boto3") {
          script {

            echo "Deploying ENV config to ${TARGET_ENV}"

            withCredentials([aws(
              accessKeyVariable: 'AWS_ACCESS_KEY_ID',
              secretKeyVariable: 'AWS_SECRET_ACCESS_KEY',
              credentialsId: 'jenkins-ecr'
            )]) {

              step([$class: 'AWSCodeDeployPublisher',
                applicationName: "tar-surge-app-${TARGET_ENV}",
                deploymentGroupName: "tar-surge-app-${TARGET_ENV}-INPLACE-deployment-group",
                deploymentConfig: "tar-surge-app-${TARGET_ENV}-config",
                region: 'us-west-2',
                s3bucket: 'dhcs-codedeploy-app',
                deploymentMethod: 'deploy',
                includes: '**',
                subdirectory: 'devops/codedeploy',
                waitForCompletion: true
              ])
            }
          }
        }
      }
    }
  }

  post {
    always  { echo "ENV deployment pipeline complete." }
    success { echo "ENV deployment successful." }
    failure { echo "ENV deployment failed." }
  }
}
