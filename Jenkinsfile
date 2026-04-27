

        if ("${SURGE_ENV}" != "PRD") {

          withCredentials([string(credentialsId: 'APPROLE_ROLE_ID', variable: 'APPROLE_ROLE_ID')]) {
            sh """#!/bin/bash
              sed -i "s|\\"{APPROLE_ROLE_ID}\\"|\\"${APPROLE_ROLE_ID}\\"|g" devops/codedeploy/environment/deploy-environment.ps1
            """
          }

          withCredentials([string(credentialsId: 'APPROLE_SECRET_ID', variable: 'APPROLE_SECRET_ID')]) {
            sh """#!/bin/bash
              sed -i "s|\\"{APPROLE_SECRET_ID}\\"|\\"${APPROLE_SECRET_ID}\\"|g" devops/codedeploy/environment/deploy-environment.ps1
            """
          }

        } else {

          withCredentials([string(credentialsId: 'APPROLE_ROLE_ID_PRD', variable: 'APPROLE_ROLE_ID')]) {
            sh """#!/bin/bash
              sed -i "s|\\"{APPROLE_ROLE_ID}\\"|\\"${APPROLE_ROLE_ID}\\"|g" devops/codedeploy/environment/deploy-environment.ps1
            """
          }

          withCredentials([string(credentialsId: 'APPROLE_SECRET_ID_PRD', variable: 'APPROLE_SECRET_ID')]) {
            sh """#!/bin/bash
              sed -i "s|\\"{APPROLE_SECRET_ID}\\"|\\"${APPROLE_SECRET_ID}\\"|g" devops/codedeploy/environment/deploy-environment.ps1
            """
          }
        }
