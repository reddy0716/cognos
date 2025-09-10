    stage('Check Instance Permissions') {
      steps {
        container('python') {
          script {
            sh '''
              cd MotioCI/api/CLI

              if [ -z "${MOTIO_AUTH_TOKEN}" ]; then
                echo "ERROR: MOTIO_AUTH_TOKEN is empty (login stage failed?)"
                exit 1
              fi

              # If your CA bundle is still being sorted out, uncomment the next line.
              # export PYTHONHTTPSVERIFY=0

              SERVER="https://cgrptmcip01.cloud.cammis.ca.gov"
              INSTANCES="Cognos-DEV/TEST Cognos-PRD"
              PROJECT="Demo"

              overall_rc=0

              for inst in $INSTANCES; do
                echo ""
                echo "===== Checking permissions for instance: $inst ====="
                inst_sanitized=$(echo "$inst" | tr '/' '_')

                echo "  -> Listing projects..."
                python3 ci-cli.py --server="$SERVER" project ls \
                  --xauthtoken="${MOTIO_AUTH_TOKEN}" \
                  --instanceName="$inst" \
                  1>"/tmp/${inst_sanitized}_projects.out" \
                  2>"/tmp/${inst_sanitized}_projects.err"
                proj_rc=$?

                if [ $proj_rc -ne 0 ] || grep -qiE "access is denied|unauthorized|forbidden|401|403" "/tmp/${inst_sanitized}_projects.err"; then
                  echo "  !! FAIL: Cannot list projects on $inst"
                  echo "----- stderr -----"
                  sed -n '1,200p' "/tmp/${inst_sanitized}_projects.err" || true
                  echo "------------------"
                  overall_rc=1
                  # No point checking labels if we can't even list projects
                  continue
                else
                  echo "  OK: Can list projects on $inst"
                fi

                echo "  -> Listing labels for project '$PROJECT'..."
                python3 ci-cli.py --server="$SERVER" label ls \
                  --xauthtoken="${MOTIO_AUTH_TOKEN}" \
                  --instanceName="$inst" \
                  --projectName="$PROJECT" \
                  1>"/tmp/${inst_sanitized}_labels.out" \
                  2>"/tmp/${inst_sanitized}_labels.err"
                lbl_rc=$?

                if [ $lbl_rc -ne 0 ] || grep -qiE "access is denied|unauthorized|forbidden|401|403" "/tmp/${inst_sanitized}_labels.err"; then
                  echo "  !! WARN: Cannot list labels for project '$PROJECT' on $inst"
                  echo "----- stderr -----"
                  sed -n '1,200p' "/tmp/${inst_sanitized}_labels.err" || true
                  echo "------------------"
                  # Label read may be restricted per project; don't fail the whole build here.
                else
                  echo "  OK: Can list labels for '$PROJECT' on $inst"
                fi
              done

              if [ "$overall_rc" -ne 0 ]; then
                echo ""
                echo "At least one instance failed basic access checks. Failing the build."
                exit $overall_rc
              fi

              echo ""
              echo "All instances passed basic access checks."
            '''
          }
        }
      }
    }
