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
  "DEV": ["SURGE_ENVNAME": "SANDBOX", "SURGE_RPM_ROOT": "E:/inetpub/ApiServices/RPM/dhcs_dev/rpm_root"],
  "SIT": ["SURGE_ENVNAME": "HOTFIX",  "SURGE_RPM_ROOT": "E:/inetpub/ApiServices/RPM/dhcs_sit/rpm_root"]
]

def SURGE_ENV

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
    env_accesskey          = ""
    env_secretkey          = ""
    env_tag_name           = ""
    env_deploy_env         = ""
    env_DEPLOY_ENVIRONMENT = "true"
    env_DEPLOY_FILES       = "false"
    env_DEPLOY_CONFIG      = "false"
  }

  stages {

    // -------------------------------------------------------------------------
    stage("Initialize") {
    // -------------------------------------------------------------------------
      steps {
        container(name: "node") {
          script {

            properties([
              parameters([
                choice(
                  name: 'DEPLOY_ENV',
                  choices: ['NONE', 'SANDBOX', 'HOTFIX'],
                  description: 'Deployment Environment'
                )
              ])
            ])

            def ENV_ALIAS_MAP = [
              "SANDBOX": "DEV",
              "HOTFIX":  "SIT"
            ]

            if (params.DEPLOY_ENV == "NONE") {
              SURGE_ENV = "NONE"
            } else {
              SURGE_ENV = ENV_ALIAS_MAP[params.DEPLOY_ENV]
            }

            deleteDir()

            checkout(scm)

            echo "Current deployment environment is ${SURGE_ENV}"

          } //END script
        } //END container node
      } //END steps
    } //END stage Initialize


    // -------------------------------------------------------------------------
    stage('Prepare Deployment') {
    // -------------------------------------------------------------------------
      when {
        expression { SURGE_ENV != "NONE" }
      }
      steps {
        container(name: "aws-boto3") {
          script {

            def surgeEnv = SURGE_ENV_CONFIG[SURGE_ENV]

            sh """#!/bin/bash
              echo "Setting up app directories — deployment will fail without these"
              mkdir -p devops/codedeploy/surgeapi
              touch devops/codedeploy/surgeapi/placeholder.txt

              echo "Replacing tokenized values in deploy-environment.ps1"
              sed -i "s,{VAULT_ADDR},${VAULT_ADDR[SURGE_ENV]}," devops/codedeploy/environment/deploy-environment.ps1
              sed -i "s,{VAULT_SECRET_PATH},${VAULT_SECRET_PATH[SURGE_ENV]}," devops/codedeploy/environment/deploy-environment.ps1
              sed -i "s,{VAULT_SECRET_PATH_LTAR},${VAULT_SECRET_PATH_LTAR[SURGE_ENV]}," devops/codedeploy/environment/deploy-environment.ps1
              sed -i "s,{VAULT_SECRET_PATH_IMGVWR},${VAULT_SECRET_PATH_IMGVWR[SURGE_ENV]}," devops/codedeploy/environment/deploy-environment.ps1
              sed -i "s,{VAULT_APPROLE_AUTH_PATH},${VAULT_APPROLE_AUTH_PATH}," devops/codedeploy/environment/deploy-environment.ps1
              sed -i "s,{SURGE_RPM_ROOT},${surgeEnv['SURGE_RPM_ROOT']}," devops/codedeploy/environment/deploy-environment.ps1
              sed -i "s,{SURGE_ENVNAME},${surgeEnv['SURGE_ENVNAME']}," devops/codedeploy/environment/deploy-environment.ps1

              echo "Replacing tokenized values in after-install.bat"
              sed -i "s,{DEPLOY_ENVIRONMENT},${env_DEPLOY_ENVIRONMENT}," devops/codedeploy/after-install.bat
            """

            withCredentials([string(credentialsId: 'APPROLE_ROLE_ID', variable: 'APPROLE_ROLE_ID')]) {
              sh """#!/bin/bash
                sed -i "s,{APPROLE_ROLE_ID},${APPROLE_ROLE_ID}," devops/codedeploy/environment/deploy-environment.ps1
              """
            }

            withCredentials([string(credentialsId: 'APPROLE_SECRET_ID', variable: 'APPROLE_SECRET_ID')]) {
              sh """#!/bin/bash
                sed -i "s,{APPROLE_SECRET_ID},${APPROLE_SECRET_ID}," devops/codedeploy/environment/deploy-environment.ps1
              """
            }

            echo "Prepare Deployment complete for environment: ${surgeEnv['SURGE_ENVNAME']}"

          } //END script
        } //END container aws-boto3
      } //END steps
    } //END stage Prepare Deployment


    // -------------------------------------------------------------------------
    stage('Deploy') {
    // -------------------------------------------------------------------------
      when {
        expression { SURGE_ENV != "NONE" }
      }
      steps {
        container(name: "aws-boto3") {
          script {

            echo "Deploying to Non-DR (us-west-2)"
            withCredentials([aws(accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: 'jenkins-ecr', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY')]) {
              step([$class: 'AWSCodeDeployPublisher',
                applicationName:     "tar-surge-app-${SURGE_ENV}",
                awsAccessKey:        "${AWS_ACCESS_KEY_ID}",
                awsSecretKey:        "${AWS_SECRET_ACCESS_KEY}",
                credentials:         'awsAccessKey',
                deploymentConfig:    "tar-surge-app-${SURGE_ENV}-config",
                deploymentGroupAppspec: false,
                deploymentGroupName: "tar-surge-app-${SURGE_ENV}-INPLACE-deployment-group",
                deploymentMethod:    'deploy',
                excludes: '', iamRoleArn: '', includes: '**',
                pollingFreqSec: 15, pollingTimeoutSec: 900,
                proxyHost: '', proxyPort: 0,
                region: 'us-west-2',
                s3bucket: 'dhcs-codedeploy-app',
                subdirectory: 'devops/codedeploy',
                versionFileName: '',
                waitForCompletion: true
              ])
            }

            if (SURGE_ENV != "DEV") {
              echo "Deploying to DR (us-east-1)"
              withCredentials([aws(accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: 'jenkins-ecr', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY')]) {
                step([$class: 'AWSCodeDeployPublisher',
                  applicationName:     "tar-surge-app-${SURGE_ENV}-DR",
                  awsAccessKey:        "${AWS_ACCESS_KEY_ID}",
                  awsSecretKey:        "${AWS_SECRET_ACCESS_KEY}",
                  credentials:         'awsAccessKey',
                  deploymentConfig:    "tar-surge-app-${SURGE_ENV}-DR-config",
                  deploymentGroupAppspec: false,
                  deploymentGroupName: "tar-surge-app-${SURGE_ENV}-DR-INPLACE-deployment-group",
                  deploymentMethod:    'deploy',
                  excludes: '', iamRoleArn: '', includes: '**',
                  pollingFreqSec: 15, pollingTimeoutSec: 900,
                  proxyHost: '', proxyPort: 0,
                  region: 'us-east-1',
                  s3bucket: 'dhcs-codedeploy-app-dr',
                  subdirectory: 'devops/codedeploy',
                  versionFileName: '',
                  waitForCompletion: true
                ])
              }
            }

          } //END script
        } //END container aws-boto3
      } //END steps
    } //END stage Deploy

  } //END stages

  // ---------------------------------------------------------------------------
  post {
  // ---------------------------------------------------------------------------
    always {
      echo "Build process complete."
    }
    success {
      echo "Build process was successful."
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
      echo "Build result changed from previous run."
    }
  } //END post

} //END pipeline
