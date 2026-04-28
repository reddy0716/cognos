Import-Module WebAdministration

$AppPoolName = "Apiservices-SBX"

Write-Host "Setting App Pool–level environment variables for $AppPoolName"

# Vault variables
Set-ItemProperty "IIS:\AppPools\$AppPoolName" -Name processModel.environmentVariables.VAULT_ADDRESS -Value "{VAULT_ADDR}"
Set-ItemProperty "IIS:\AppPools\$AppPoolName" -Name processModel.environmentVariables.VAULT_APPROLE_ROLE_ID -Value "{APPROLE_ROLE_ID}"
Set-ItemProperty "IIS:\AppPools\$AppPoolName" -Name processModel.environmentVariables.VAULT_APPROLE_SECRET_ID -Value "{APPROLE_SECRET_ID}"
Set-ItemProperty "IIS:\AppPools\$AppPoolName" -Name processModel.environmentVariables.VAULT_SECRET_PATH -Value "{VAULT_SECRET_PATH}"
Set-ItemProperty "IIS:\AppPools\$AppPoolName" -Name processModel.environmentVariables.VAULT_SECRET_PATH_LTAR -Value "{VAULT_SECRET_PATH_LTAR}"
Set-ItemProperty "IIS:\AppPools\$AppPoolName" -Name processModel.environmentVariables.VAULT_SECRET_PATH_IMGVWR -Value "{VAULT_SECRET_PATH_IMGVWR}"
Set-ItemProperty "IIS:\AppPools\$AppPoolName" -Name processModel.environmentVariables.VAULT_APPROLE_AUTH_PATH -Value "{VAULT_APPROLE_AUTH_PATH}"

# Surge variables
Set-ItemProperty "IIS:\AppPools\$AppPoolName" -Name processModel.environmentVariables.SURGE_ENVNAME -Value "{SURGE_ENVNAME}"
Set-ItemProperty "IIS:\AppPools\$AppPoolName" -Name processModel.environmentVariables.SURGE_RPM_ROOT -Value "{SURGE_RPM_ROOT}"
Set-ItemProperty "IIS:\AppPools\$AppPoolName" -Name processModel.environmentVariables.SURGE_RPM_ONLINE_KEY -Value "/online"

# Datadog
Set-ItemProperty "IIS:\AppPools\$AppPoolName" -Name processModel.environmentVariables.DD_LOGS_ENABLED -Value "true"

Write-Host "Environment variables applied to AppPool"

# Restart AppPool to apply changes
Restart-WebAppPool $AppPoolName

Write-Host "AppPool restarted"
