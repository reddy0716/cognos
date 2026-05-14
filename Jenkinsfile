/*
 =======================================================================================
 This file is being updated constantly by the DevOps team to introduce new enhancements
 based on the template.  If you have suggestions for improvement,
 please contact the DevOps team so that we can incorporate the changes into the
 template.  In the meantime, if you have made changes here or don't want this file to be
 updated, please indicate so at the beginning of this file.
 =======================================================================================
 */

def branch     = env.BRANCH_NAME ?: "sandbox00"
def workingDir = "/home/jenkins/agent"

// Maps the target environment to the source environment it promotes FROM
// sandbox = DEV, hotfix = SIT, prd = PRD
def DEPLOY_FROM_ENV = [
  "hotfix": "sandbox",
  "prd":    "hotfix"
]

// Maps friendly env names to CodeDeploy application name suffixes
def CODEDEPLOY_ENV_MAP = [
  "hotfix": "SIT",
  "prd":    "PRD"
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
    timeout(time: 3, unit: "HOURS")
    skipDefaultCheckout()
    buildDiscarder(logRotator(numToKeepStr: "20"))
  }

  environment {
    env_promotion_to_environment   = ""
    env_promotion_from_environment = ""
  }

  stages {

    // -------------------------------------------------------------------------
    stage("Initialize") {
    // -------------------------------------------------------------------------
      steps {
        container("node") {
          script {

            properties([
              parameters([
                choice(
                  name: 'PROMOTE_TO_ENV',
                  choices: ['NONE', 'hotfix', 'prd'],
                  description: 'Target environment to promote to (sandbox->hotfix or hotfix->prd)'
                )
              ])
            ])

            if (params.PROMOTE_TO_ENV == "NONE") {
              currentBuild.result = 'ABORTED'
              error("No environment selected. Aborting.")
            }

            env_promotion_to_environment   = params.PROMOTE_TO_ENV
            env_promotion_from_environment = DEPLOY_FROM_ENV["${env_promotion_to_environment}"]
            SURGE_ENV                      = CODEDEPLOY_ENV_MAP["${env_promotion_to_environment}"]

            deleteDir()

            checkout(scm)

            echo "Promoting FROM : ${env_promotion_from_environment}"
            echo "Promoting TO   : ${env_promotion_to_environment}"
            echo "CodeDeploy ENV : ${SURGE_ENV}"

          } //END script
        } //END container node
      } //END steps
    } //END stage Initialize


    // -------------------------------------------------------------------------
    stage("Build") {
    // -------------------------------------------------------------------------
      steps {
        container("dotnet") {
          script {
            lock(resource: "deployments-github-repo") {
              dir("${WORKSPACE}/deployrepo") {
                withCredentials([usernamePassword(credentialsId: "github-key", usernameVariable: 'NUSER', passwordVariable: 'NPASS')]) {
                  sh """
                    #!/bin/bash
                    set -e

                    BASE="\$PWD"

                    FROM_ENV="${env_promotion_from_environment}"
                    TO_ENV="${env_promotion_to_environment}"
                    FROM_ENV_UPPER=\$(echo "\$FROM_ENV" | tr '[:lower:]' '[:upper:]')
                    TO_ENV_UPPER=\$(echo "\$TO_ENV"   | tr '[:lower:]' '[:upper:]')

                    echo "FROM: \$FROM_ENV_UPPER  TO: \$TO_ENV_UPPER"

                    # Clone repos
                    git clone https://${NUSER}:${NPASS}@github.com/ca-mmis/tar-surge-client.git \\
                      --branch ${branch} --single-branch --depth=1
                    git clone https://${NUSER}:${NPASS}@github.com/ca-mmis/deployments-combined-devops.git \\
                      --branch master --single-branch --depth=1

                    git config --global user.email "jenkins@cammis.com"
                    git config --global user.name  "jenkins"

                    # Prepare codedeploy/SurgeUpdate
                    mkdir -p tar-surge-client/devops/codedeploy
                    rm -rf tar-surge-client/devops/codedeploy/SurgeUpdate
                    mkdir -p tar-surge-client/devops/codedeploy/SurgeUpdate

                    # Unzip lower-env package into codedeploy
                    cd "\$BASE/deployments-combined-devops"
                    git pull
                    unzip -o "SurgeAutoupdate/\$FROM_ENV/SurgeUpdate/SurgeUpdate_\$FROM_ENV_UPPER.ZIP" \\
                      -d "\$BASE/tar-surge-client/devops/codedeploy/SurgeUpdate"

                    # Overlay target env configs
                    for f in appsettings.json nlog.config icon.ico; do
                      SRC="\$BASE/tar-surge-client/Config/\$TO_ENV_UPPER/\$f"
                      DST="\$BASE/tar-surge-client/devops/codedeploy/SurgeUpdate/\$f"
                      [ -f "\$SRC" ] && cp "\$SRC" "\$DST" || echo "WARN: missing \$SRC"
                    done

                    # Copy only target env BAT script into SurgeUpdate (for CodeDeploy bundle)
                    cd "\$BASE/tar-surge-client/devops/codedeploy"
                    rm -f SurgeUpdate/SurgeInstall_*.bat || true
                    BAT_SRC="\$BASE/tar-surge-client/Config/\$TO_ENV_UPPER/SurgeInstall_\$TO_ENV_UPPER.bat"
                    BAT_DST="SurgeUpdate/SurgeInstall_\$TO_ENV_UPPER.bat"
                    [ -f "\$BAT_SRC" ] && cp "\$BAT_SRC" "\$BAT_DST" || echo "WARN: missing \$BAT_SRC"

                    # Build ZIP for target env (used by CodeDeploy + deployments-combined-devops)
                    cd SurgeUpdate
                    rm -f "../SurgeUpdate_\$TO_ENV_UPPER.ZIP" || true
                    zip -r "../SurgeUpdate_\$TO_ENV_UPPER.ZIP" .
                    cp "../SurgeUpdate_\$TO_ENV_UPPER.ZIP" ../SurgeUpdate/
                    cd ..

                    # Update deployments-combined-devops with promoted ZIP
                    cd "\$BASE/deployments-combined-devops"
                    mkdir -p "SurgeAutoupdate/\$TO_ENV/SurgeUpdate"
                    cp "\$BASE/tar-surge-client/devops/codedeploy/SurgeUpdate_\$TO_ENV_UPPER.ZIP" \\
                       "SurgeAutoupdate/\$TO_ENV/SurgeUpdate/"

                    git add "SurgeAutoupdate/\$TO_ENV/SurgeUpdate/SurgeUpdate_\$TO_ENV_UPPER.ZIP" || true
                    git commit -m "Promoted SurgeUpdate ZIP from \$FROM_ENV_UPPER to \$TO_ENV_UPPER" || true
                    git push || true

                    echo "Build stage complete. ZIP ready for CodeDeploy."
                  """
                } //END withCredentials
              } //END dir
            } //END lock
          } //END script
        } //END container dotnet
      } //END steps
    } //END stage Build


    // -------------------------------------------------------------------------
    stage("Deploy") {
    // -------------------------------------------------------------------------
      steps {
        container(name: "aws-boto3") {
          script {

            echo "Deploying via AWS CodeDeploy to ${SURGE_ENV}"

            // Non-DR deployment - us-west-2
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
                subdirectory: 'deployrepo/tar-surge-client/devops/codedeploy',
                versionFileName: '',
                waitForCompletion: true
              ])
            }

            // DR deployment - us-east-1
            // Skipped for sandbox/DEV — only SIT and PRD have DR
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
                subdirectory: 'deployrepo/tar-surge-client/devops/codedeploy',
                versionFileName: '',
                waitForCompletion: true
              ])
            }

          } //END script
        } //END container aws-boto3
      } //END steps
    } //END stage Deploy


    // -------------------------------------------------------------------------
    stage("Push Artifacts to Deployment Repo") {
    // -------------------------------------------------------------------------
      steps {
        container("dotnet") {
          script {
            lock(resource: 'deployments-github-repo', inversePrecedence: false) {
              dir("${WORKSPACE}/deployrepo") {
                withCredentials([usernamePassword(credentialsId: "github-key", usernameVariable: 'NUSER', passwordVariable: 'NPASS')]) {
                  sh """
                    #!/bin/bash
                    set -e

                    BASE="\$PWD"
                    TO_ENV="${env_promotion_to_environment}"
                    TO_ENV_UPPER=\$(echo "\$TO_ENV" | tr '[:lower:]' '[:upper:]')

                    # Rebuild a CLEAN ZIP for Git repo (NO SurgeInstall_*.bat inside)
                    cd "\$BASE/tar-surge-client/devops/codedeploy/SurgeUpdate"
                    rm -f SurgeInstall_*.bat || true
                    rm -f "../SurgeUpdate_\$TO_ENV_UPPER.ZIP" || true
                    zip -r "../SurgeUpdate_\$TO_ENV_UPPER.ZIP" .

                    # Clone tar-surge-client-deployment repo
                    cd "\$BASE"
                    git clone https://${NUSER}:${NPASS}@github.com/ca-mmis/tar-surge-client-deployment.git --depth=1 || true

                    cd tar-surge-client-deployment
                    git checkout master
                    git pull

                    mkdir -p tar-surge-client/

                    # Copy CLEAN ZIP (no BAT inside) + external BAT side by side
                    cp "\$BASE/tar-surge-client/devops/codedeploy/SurgeUpdate_\$TO_ENV_UPPER.ZIP" \\
                       tar-surge-client/
                    cp "\$BASE/tar-surge-client/Config/\$TO_ENV_UPPER/SurgeInstall_\$TO_ENV_UPPER.bat" \\
                       tar-surge-client/

                    if [[ -n \$(git status --porcelain) ]]; then
                      git add .
                      git commit -m "Automated SurgeUpdate promotion to \$TO_ENV_UPPER"
                      git push origin master
                    fi

                    git tag -f -a "SURGE-\$TO_ENV_UPPER" -m "Promoting Thickclient to \$TO_ENV_UPPER"
                    git push origin "SURGE-\$TO_ENV_UPPER" --force || true

                    echo "tar-surge-client-deployment updated for \$TO_ENV_UPPER"
                  """
                } //END withCredentials
              } //END dir
            } //END lock
          } //END script
        } //END container dotnet
      } //END steps
    } //END stage Push Artifacts

  } //END stages

  // ---------------------------------------------------------------------------
  post {
  // ---------------------------------------------------------------------------
    always {
      echo "Promotion pipeline complete."
    }
    success {
      echo "Promotion to ${env_promotion_to_environment} succeeded."
    }
    failure {
      echo "Promotion to ${env_promotion_to_environment} failed."
    }
    aborted {
      echo "Promotion pipeline aborted."
    }
  } //END post

} //END pipeline
