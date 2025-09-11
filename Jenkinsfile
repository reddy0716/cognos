
def branch     = env.BRANCH_NAME ?: "DEV"
def namespace  = env.NAMESPACE   ?: "dev"
def cloudName  = env.CLOUD_NAME == "openshift" ? "openshift" : "kubernetes"
def workingDir = "/home/jenkins/agent"

APP_NAME = "combined-devops-cognos-deployments"

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
      image: registry.access.redhat.com/ubi8/nodejs-16:latest
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
    - name: python
      image: 136299550619.dkr.ecr.us-west-2.amazonaws.com/cammisboto3:1.2.0
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
        - name: REQUESTS_CA_BUNDLE
          value: "/etc/pki/tls/certs/ca-bundle.crt"
        - name: SSL_CERT_FILE
          value: "/etc/pki/tls/certs/ca-bundle.crt"
        - name: CI
          value: "1"
      volumeMounts:
        - name: jenkins-trusted-ca-bundle
          mountPath: /etc/pki/tls/certs
"""
    }
  }

  // Defaults match your current setup; you can override later via parameters if needed.
  
  options {
    disableConcurrentBuilds()
    timestamps()
  }

  stages {
    stage('Initialize') {
      steps {
        echo "Branch: ${env.GIT_BRANCH}"
        echo "Initializing MotioCI Cognos deployment pipeline..."
      }
    }

    stage('Check Python Availability') {
      steps {
        container('node') {
          sh '''
            set -e
            echo "Checking for Python3..."
            which python3 || true
            python3 --version || true
          '''
        }
      }
    }

    stage('Install CLI deps') {
      steps {
        container('python') {
          sh '''
            set -euo pipefail
            
            python3 -m pip install --user -r requirements.txt
            echo "Dependencies installed."
          '''
        }
      }
    }

 
  post {
    always {
      // Keep evidence even on failure
      archiveArtifacts artifacts: 'namespaces_target.json, MotioCI/deploy_output.json, MotioCI/verify_labels.json, projects_target.json', onlyIfSuccessful: false
      echo "Pipeline execution finished."
    }
    success {
      echo "MotioCI pipeline completed successfully."
    }
    failure {
      echo "MotioCI pipeline failed."
    }
  }
}
