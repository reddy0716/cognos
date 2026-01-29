Import-Module WebAdministration

# Variables
$SiteName    = "Apiservices-SBX"
$AppPoolName = "Apiservices-SBX"

# Stop Site and App Pool
Write-Host "Stopping $SiteName"
Stop-Web-Site $SiteName
Start-Sleep -Seconds 5

Write-Host "Stopping Application Pool $AppPoolName"
Stop-Web-App-Pool $AppPoolName
Start-Sleep -Seconds 5

Write-Host "Status of Application Pool"
Get-IISAppPool -Name $AppPoolName

# ================================
# SET APP POOL LEVEL ENV VARIABLES
# ================================

Write-Host "Setting App Pool–level environment variables for $AppPoolName"

$envVars = @{
    VAULT_ADDRESS              = "{VAULT_ADDR}"
    VAULT_APPROLE_ROLE_ID      = "{APPROLE_ROLE_ID}"
    VAULT_APPROLE_SECRET_ID    = "{APPROLE_SECRET_ID}"
    VAULT_SECRET_PATH          = "{VAULT_SECRET_PATH}"
    VAULT_SECRET_PATH_LTAR     = "{VAULT_SECRET_PATH_LTAR}"
    VAULT_SECRET_PATH_IMGVWR   = "{VAULT_SECRET_PATH_IMGVWR}"
    VAULT_APPROLE_AUTH_PATH    = "{VAULT_APPROLE_AUTH_PATH}"

    SURGE_ENVNAME              = "{SURGE_ENVNAME}"
    SURGE_RPM_ROOT             = "{SURGE_RPM_ROOT}"
    SURGE_RPM_ONLINE_KEY       = "/online"

    DD_LOGS_ENABLED            = "true"
}

Set-ItemProperty "IIS:\AppPools\$AppPoolName" `
  -Name processModel.environmentVariables `
  -Value $envVars

Write-Host "App Pool environment variables applied successfully"

# ================================
# START APP POOL & SITE
# ================================

Write-Host "Starting Application Pool $AppPoolName"
Start-WebAppPool -Name $AppPoolName

Write-Host "Starting Website $SiteName"
Start-Website -Name $SiteName

Write-Host "Environment Deploy Complete (App Pool scoped)"
