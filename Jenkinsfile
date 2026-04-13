# ================================
# SET APP POOL LEVEL ENV VARIABLES
# ================================

Write-Host "Setting App Pool–level environment variables for $AppPoolName"

# Format RPM root path
$rawRpmRoot = "{SURGE_RPM_ROOT}"
$formattedRpmRoot = $rawRpmRoot -replace '/', [char]92

# Define all environment variables
$envVars = @{
    VAULT_ADDRESS              = "{VAULT_ADDR}"
    VAULT_APPROLE_ROLE_ID      = "{APPROLE_ROLE_ID}"
    VAULT_APPROLE_SECRET_ID    = "{APPROLE_SECRET_ID}"
    VAULT_SECRET_PATH          = "{VAULT_SECRET_PATH}"
    VAULT_SECRET_PATH_LTAR     = "{VAULT_SECRET_PATH_LTAR}"
    VAULT_SECRET_PATH_IMGVWR   = "{VAULT_SECRET_PATH_IMGVWR}"
    VAULT_APPROLE_AUTH_PATH    = "{VAULT_APPROLE_AUTH_PATH}"

    APIPath                    = "{SURGE_API_PATH}"
    SURGE_ENVNAME              = "{SURGE_ENVNAME}"
    SURGE_RPM_ONLINE_KEY       = "/online"
    SURGE_RPM_ROOT             = $formattedRpmRoot

    DD_LOGS_ENABLED            = "true"
}

# Apply to App Pool only
Set-ItemProperty "IIS:\AppPools\$AppPoolName" `
  -Name processModel.environmentVariables `
  -Value $envVars

Write-Host "App Pool environment variables applied successfully"
