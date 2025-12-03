echo "Removing BAT script from SurgeUpdate before zipping..."
rm -f ${WORKSPACE}/devops/codedeploy/SurgeUpdate/SurgeInstall_${env_deploy_env}.bat || true

echo "Recreating ZIP again without BAT script..."
cd ${WORKSPACE}/devops/codedeploy
rm -f SurgeUpdate/SurgeUpdate_${env_deploy_env}.ZIP
zip -r SurgeUpdate/SurgeUpdate_${env_deploy_env}.ZIP SurgeUpdate

echo "Copying ZIP and BAT script separately..."
