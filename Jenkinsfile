# ---------------------------------------------------------------------------
# Apply environment variables via appcmd.exe
# ---------------------------------------------------------------------------

Write-Host "--- Applying environment variables via appcmd.exe ---"

$formattedRpmRoot = $SurgeRpmRoot -replace '/', [char]92
$poolFilter = "system.applicationHost/applicationPools/add[@name='$AppPoolName']/environmentVariables"

# Clear the pool's existing environmentVariables collection
Write-Host "Clearing existing environment variables..."
Clear-WebConfiguration -PSPath 'MACHINE/WEBROOT/APPHOST' -Filter $poolFilter -ErrorAction Stop

$remaining = @(Get-WebConfiguration -PSPath 'MACHINE/WEBROOT/APPHOST' -Filter "$poolFilter/add")
if ($remaining.Count -gt 0) {
    Write-Error "Failed to clear environment variables ($($remaining.Count) still present)"
    Exit 1
}
Write-Host "Existing environment variables cleared"

$envVars = [ordered]@{
    VAULT_ADDRESS            = $VaultAddress
    VAULT_APPROLE_ROLE_ID    = $VaultAppRoleRoleId
    VAULT_APPROLE_SECRET_ID  = $VaultAppRoleSecretId
    VAULT_SECRET_PATH        = $VaultSecretPath
    VAULT_SECRET_PATH_LTAR   = $VaultSecretPathLtar
    VAULT_SECRET_PATH_IMGVWR = $VaultSecretPathImgVwr
    VAULT_APPROLE_AUTH_PATH  = $VaultAppRoleAuthPath
    APIPath                  = $SurgeApiPath
    SURGE_ENVNAME            = $SurgeEnvName
    SURGE_RPM_ROOT           = $formattedRpmRoot
    SURGE_RPM_ONLINE_KEY     = "/online"
    DD_LOGS_ENABLED          = "true"
}

foreach ($kv in $envVars.GetEnumerator()) {
    $name  = $kv.Key
    $value = $kv.Value

    if ([string]::IsNullOrEmpty($value) -or $value -match '^\{.+\}$') {
        Write-Error "Environment variable $name is empty or still tokenized ('$value') - check Jenkins sed step"
        Exit 1
    }

    & $appcmd set config -section:system.applicationHost/applicationPools `
        "/+[name='$AppPoolName'].environmentVariables.[name='$name',value='$value']" `
        /commit:apphost | Out-Null

    if ($LASTEXITCODE -ne 0) {
        Write-Error "Failed to set environment variable: $name (exit code $LASTEXITCODE)"
        Exit 1
    }

    if ($name -match "ROLE_ID|SECRET_ID") { Write-Host "    Set: $name = ****" }
    else                                  { Write-Host "    Set: $name = $value" }
}

Write-Host "All environment variables applied via appcmd.exe"
