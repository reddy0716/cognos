Import-Module WebAdministration

$AppPoolName = "Apiservices-SBX"

Write-Host "Setting App Pool–level environment variables for $AppPoolName"

$envVars = @{
    VAULT_ADDRESS              = "https://np.secrets.cammis.medi-cal.ca.gov/v1/"
    VAULT_APPROLE_ROLE_ID      = "APPROLE_ROLE_ID"
    VAULT_APPROLE_SECRET_ID    = "APPROLE_SECRET_ID"
    VAULT_SECRET_PATH          = "kv-dev/data/us-west/dev-tar/tar-surgenet-service-secrets"
    VAULT_SECRET_PATH_LTAR     = "kv-dev/data/us-west/dev-tar/tar-ltar-service-secrets"
    VAULT_SECRET_PATH_IMGVWR   = "kv-dev/data/us-west/dev-tar/tar-image-viewer-service-secrets"
    VAULT_APPROLE_AUTH_PATH    = "auth/approle/login"

    SURGE_ENVNAME              = "SANDBOX"
    SURGE_RPM_ROOT             = "E:/inetpub/ApiServices/RPM/dhcs_dev/rpm_root"
    SURGE_RPM_ONLINE_KEY       = "/online"

    DD_LOGS_ENABLED            = "true"
}

Set-ItemProperty "IIS:\AppPools\$AppPoolName" `
  -Name processModel.environmentVariables `
  -Value $envVars

Write-Host "App Pool environment variables applied successfully"

Restart-WebAppPool $AppPoolName
