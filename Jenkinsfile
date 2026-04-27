echo "Replacing tokenized values for accessing Vault"

sed -i "s|{VAULT_ADDR}|${VAULT_ADDR["${SURGE_ENV}"]}|g" devops/codedeploy/environment/deploy-environment.ps1
sed -i "s|{VAULT_SECRET_PATH}|${VAULT_SECRET_PATH["${SURGE_ENV}"]}|g" devops/codedeploy/environment/deploy-environment.ps1
sed -i "s|{VAULT_SECRET_PATH_LTAR}|${VAULT_SECRET_PATH_LTAR["${SURGE_ENV}"]}|g" devops/codedeploy/environment/deploy-environment.ps1
sed -i "s|{VAULT_SECRET_PATH_IMGVWR}|${VAULT_SECRET_PATH_IMGVWR["${SURGE_ENV}"]}|g" devops/codedeploy/environment/deploy-environment.ps1
sed -i "s|{VAULT_APPROLE_AUTH_PATH}|${VAULT_APPROLE_AUTH_PATH}|g" devops/codedeploy/environment/deploy-environment.ps1

sed -i "s|{SURGE_ENVNAME}|${surgeEnv["SURGE_ENVNAME"]}|g" devops/codedeploy/environment/deploy-environment.ps1
sed -i "s|{SURGE_RPM_ROOT}|${surgeEnv["SURGE_RPM_ROOT"]}|g" devops/codedeploy/environment/deploy-environment.ps1

sed -i "s|{DEPLOY_ENVIRONMENT}|${env_DEPLOY_ENVIRONMENT}|g" devops/codedeploy/after-install.bat
