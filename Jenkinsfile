/*
=======================================================================================
This file is being updated constantly by the DevOps team to introduce new enhancements
based on the template.  If you have suggestions for improvement,
please contact the DevOps team so that we can incorporate the changes into the
template.  In the meantime, if you have made changes here or don't want this file to be
updated, please indicate so at the beginning of this file.
=======================================================================================
*/

def branch = env.BRANCH_NAME ?: "Dev"
def workingDir = "/home/jenkins/agent"

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
              securityContext:
                privileged: true
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
    env_current_git_commit=""
    env_accesskey=""
    env_secretkey=""
    env_tag_name=""
    env_deploy_env=""
    env_skip_deploy="true"
    env_DEPLOY_ENVIRONMENT="false"
    env_DEPLOY_FILES="true"
    env_DEPLOY_CONFIG="false"
    env_release_type=""
  }

  stages {
   stage("Initialize") {
    steps {
        container(name: "node") {
            script {
                properties([
                    parameters([
                        choice(name: 'RELEASE_TYPE', choices: ['PATCH', 'MINOR', 'MAJOR'], description: 'Enter Release type'),
                        booleanParam(name: 'USE_GIT_TAG', defaultValue: false, description: 'Use the selected git tag instead of LATEST commit'),
                        gitParameter(name: 'GIT_TAG', defaultValue: 'tar-surge-app_from_dev', description: 'Git tag to deploy', type: 'PT_TAG'),
                        string(name: 'GIT_SHA', defaultValue: 'enter git sha(8+ chars)', description: 'Enter git SHA to deploy')
                    ])
                ])

                deleteDir()

                echo 'Checking out source and retrieving commit ID...'
                env_current_git_commit = checkout(scm).GIT_COMMIT

                // Get the short version of the commit hash
                env_current_git_commit = "${env_current_git_commit[0..7]}"

                env_deploy_env = "DEV"
                echo "Current deployment environment: ${env_deploy_env}"
		def repositories = [
                        [name: 'tar-surge-client', branch: 'Dev', url: 'https://github.com/ca-mmis/tar-surge-client.git'],
                        [name: 'tar-surge-app', branch: 'master', url: 'https://github.com/ca-mmis/tar-surge-app.git']
                    ]

                    repositories.each { repo ->
                        dir(repo.name) {
                            git branch: repo.branch, credentialsId: 'github-key', url: repo.url
                        }
                    }  
            }
        }
    }
}

   stage('Build & Package') {
      steps {
        container("dotnet") {
          script {
            sh '''
              echo "Building thick client..."
              mkdir -p devops/codedeploy/SurgeUpdate
              dotnet publish tar-surge-client/Cammis.Surge.Client.sln \
                -o build_output -c Release -r win-x64 --self-contained true /p:EnableWindowsTargeting=true
            '''

            // Extract EXE version
            def exePath = "build_output/Cammis.Surge.Client.exe"
            def exeVersion = powershell(
              returnStdout: true,
              script: "(Get-Item '${exePath}').VersionInfo.FileVersion"
            ).trim()

            echo "Detected EXE version: ${exeVersion}"
            env.SURGE_VERSION = exeVersion

            // Write Version.TXT + overlay configs
            sh """
              echo '${exeVersion}' > devops/codedeploy/SurgeUpdate/Version.TXT
              cp -r build_output/* devops/codedeploy/SurgeUpdate/
              
              # Note: nested path is correct because surge-app repo has tar-surge-client/config
              cp tar-surge-app/tar-surge-client/config/${env_deploy_env}/* devops/codedeploy/SurgeUpdate/
              
              cd devops/codedeploy
              zip -r SurgeUpdate_${env_deploy_env}.ZIP SurgeUpdate
            """
          }
        }
      }
    }

	  
  
    stage('Deploy') {
      steps {
        lock(resource: 'codedeploy-ec2-lock') {
          container("aws-boto3") {
            script {
              echo "Deploy Using AWS CodeDeploy..."
              withCredentials([aws(accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: 'jenkins-ecr-ecs', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY')]) {
                step([$class: 'AWSCodeDeployPublisher',
                  applicationName: "tar-surge-app-${env_deploy_env}",
                  awsAccessKey: "${AWS_ACCESS_KEY_ID}",
                  awsSecretKey: "${AWS_SECRET_ACCESS_KEY}",
                  credentials: 'awsAccessKey',
                  deploymentConfig: "tar-surge-app-${env_deploy_env}-config",
                  deploymentGroupAppspec: false,
                  deploymentGroupName: "tar-surge-app-${env_deploy_env}-INPLACE-deployment-group",
                  deploymentMethod: 'deploy',
                  includes: '**',
                  region: 'us-west-2',
                  s3bucket: 'dhcs-codedeploy-app',
                  subdirectory: 'devops/codedeploy',
                  waitForCompletion: true])
              }
            }
          }
        }

        // Push artifacts into deployment repos
        container("jnlp") {
          lock(resource: 'deployments-github-repo', inversePrecedence: false) {
            dir("${WORKSPACE}/deployrepo") {
              withCredentials([usernamePassword(credentialsId: "github-key", usernameVariable: 'NUSER', passwordVariable: 'NPASS')]) {
                sh """
                  set -e
                  git clone https://${NUSER}:${NPASS}@github.com/ca-mmis/tar-surge-client-deployment.git --depth=1
                  git config --global user.email "jenkins@cammis.com"
                  git config --global user.name "jenkins"

                  cd tar-surge-client-deployment
                  git checkout master
                  git pull

                  cp ${WORKSPACE}/devops/codedeploy/SurgeUpdate_DEV.ZIP tar-surge-client/
                  cp ${WORKSPACE}/devops/codedeploy/SurgeUpdate/Version.TXT tar-surge-client/

                  if [[ -n \$(git status --porcelain) ]]; then
                    git add .
                    git commit -m "Automated commit - Deploying SurgeUpdate artifacts"
                    git push origin master
                  fi

                  git tag -f -a "${env_tag_name}" -m "Deploying Thickclient - Tag ${env_tag_name}"
                  git push origin "${env_tag_name}" --force
                """

                sh """
                  git clone https://${NUSER}:${NPASS}@github.com/ca-mmis/deployments-combined-devops.git --depth=1
                  cd deployments-combined-devops
                  git checkout master
                  git pull

                  rm -rf tar-surge-client/dev/surgeupdate/*
                  cp -a ${WORKSPACE}/devops/codedeploy/SurgeUpdate/. tar-surge-client/dev/surgeupdate/

                  git add .
                  git commit -m "Updated build artifacts for tar-surge-client build ${env_tag_name}" || true
                  git push https://${NUSER}:${NPASS}@github.com/ca-mmis/deployments-combined-devops.git
                """
              }
            }
          }
        }
      }
    }
  }

  post {
    always { echo "Build Process complete." }
    success { echo "Build and Deploy succeeded." }
    failure { echo "Build or Deploy failed." }
    aborted { echo "Build aborted." }
  }
}
