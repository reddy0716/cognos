$VaultAddress           = "{VAULT_ADDR}"
$VaultAppRoleRoleId     = "{APPROLE_ROLE_ID}"
$VaultAppRoleSecretId   = "{APPROLE_SECRET_ID}"
$VaultSecretPath        = "{VAULT_SECRET_PATH}"
$VaultSecretPathLtar    = "{VAULT_SECRET_PATH_LTAR}"
$VaultSecretPathImgVwr  = "{VAULT_SECRET_PATH_IMGVWR}"
$VaultAppRoleAuthPath   = "{VAULT_APPROLE_AUTH_PATH}"
$SurgeEnvName           = "{SURGE_ENVNAME}"
$SurgeRpmRoot           = "{SURGE_RPM_ROOT}"


# ------------------------------------------------------------------------------
# Derived IIS names — built from the injected environment token so this script
# works correctly for both SANDBOX and HOTFIX without any hardcoding.
# ------------------------------------------------------------------------------

$AppPoolName = "Apiservices-$SurgeEnvName"
$SiteName    = "Apiservices-$SurgeEnvName"

Write-Host "============================================================"
Write-Host "  Environment : $SurgeEnvName"
Write-Host "  IIS Site    : $SiteName"
Write-Host "  App Pool    : $AppPoolName"
Write-Host "  Vault Addr  : $VaultAddress"
Write-Host "  RPM Root    : $SurgeRpmRoot"
Write-Host "============================================================"


# ------------------------------------------------------------------------------
# Validate IIS module is available before proceeding
# ------------------------------------------------------------------------------

if (-not (Get-Module -ListAvailable -Name WebAdministration)) {
    Write-Error "WebAdministration module not found. Ensure IIS is installed."
    Exit 1
}
Import-Module WebAdministration
Write-Host "WebAdministration module loaded"


# ------------------------------------------------------------------------------
# Verify the site and app pool exist before attempting to stop them
# ------------------------------------------------------------------------------

if (-not (Test-Path "IIS:\Sites\$SiteName")) {
    Write-Error "IIS site '$SiteName' does not exist. Verify IIS configuration."
    Exit 1
}

if (-not (Test-Path "IIS:\AppPools\$AppPoolName")) {
    Write-Error "IIS app pool '$AppPoolName' does not exist. Verify IIS configuration."
    Exit 1
}


# ------------------------------------------------------------------------------
# Stop site and app pool
# ------------------------------------------------------------------------------

Write-Host ""
Write-Host "--- Stopping IIS site and app pool ---"

Stop-Web-Site($SiteName)

Write-Host "Sleeping 5 seconds after site stop"
Start-Sleep -Seconds 5

Stop-Web-App-Pool($AppPoolName)

Write-Host "Sleeping 5 seconds after app pool stop"
Start-Sleep -Seconds 5

Write-Host "App pool state after stop:"
Get-IISAppPool -Name $AppPoolName | Select-Object Name, State


# ------------------------------------------------------------------------------
# Build the environment variable hashtable
# ------------------------------------------------------------------------------

$envVars = @{
    VAULT_ADDRESS              = $VaultAddress
    VAULT_APPROLE_ROLE_ID      = $VaultAppRoleRoleId
    VAULT_APPROLE_SECRET_ID    = $VaultAppRoleSecretId
    VAULT_SECRET_PATH          = $VaultSecretPath
    VAULT_SECRET_PATH_LTAR     = $VaultSecretPathLtar
    VAULT_SECRET_PATH_IMGVWR   = $VaultSecretPathImgVwr
    VAULT_APPROLE_AUTH_PATH    = $VaultAppRoleAuthPath
    SURGE_ENVNAME              = $SurgeEnvName
    SURGE_RPM_ROOT             = $SurgeRpmRoot
    SURGE_RPM_ONLINE_KEY       = "/online"
    DD_LOGS_ENABLED            = "true"
}


# ------------------------------------------------------------------------------
# Apply environment variables via Microsoft.Web.Administration API
# ------------------------------------------------------------------------------

Write-Host ""
Write-Host "--- Applying environment variables via Microsoft.Web.Administration API ---"

try {
    Add-Type -AssemblyName "Microsoft.Web.Administration"

    $serverManager = New-Object Microsoft.Web.Administration.ServerManager
    $pool = $serverManager.ApplicationPools[$AppPoolName]

    if ($null -eq $pool) {
        Write-Error "ServerManager could not find app pool '$AppPoolName'."
        Exit 1
    }

    # Clear existing env vars so stale keys do not persist between deployments
    $pool.EnvironmentVariables.Clear()
    Write-Host "Cleared existing environment variables from app pool"

    $envVars.GetEnumerator() | ForEach-Object {
        $env = $pool.EnvironmentVariables.CreateElement("add")
        $env.Attributes["name"].Value  = $_.Key
        $env.Attributes["value"].Value = $_.Value
        $pool.EnvironmentVariables.Add($env)

        if ($_.Key -match "ROLE_ID|SECRET_ID") {
            Write-Host "    Set: $($_.Key) = ****"
        } else {
            Write-Host "    Set: $($_.Key) = $($_.Value)"
        }
    }

    $serverManager.CommitChanges()
    Write-Host "Environment variables committed via ServerManager API"
}
catch {
    Write-Error "Failed to apply environment variables via ServerManager API: $_"
    Exit 1
}
finally {
    if ($null -ne $serverManager) {
        $serverManager.Dispose()
        Write-Host "ServerManager disposed"
    }
}


# ------------------------------------------------------------------------------
# Verify env vars were written correctly by reading back from IIS
# ------------------------------------------------------------------------------

Write-Host ""
Write-Host "--- Verifying environment variables written to app pool ---"

try {
    $verifyManager = New-Object Microsoft.Web.Administration.ServerManager
    $verifyPool    = $verifyManager.ApplicationPools[$AppPoolName]

    $verifyPool.EnvironmentVariables | ForEach-Object {
        if ($_.Name -match "ROLE_ID|SECRET_ID") {
            Write-Host "    $($_.Name) = ****"
        } else {
            Write-Host "    $($_.Name) = $($_.Value)"
        }
    }
    $verifyManager.Dispose()
}
catch {
    Write-Warning "Could not read back environment variables for verification: $_"
}


# ------------------------------------------------------------------------------
# Start app pool and site
# ------------------------------------------------------------------------------

Write-Host ""
Write-Host "--- Starting IIS app pool and site ---"

Start-Web-App-Pool($AppPoolName)

Write-Host "Sleeping 5 seconds after app pool start"
Start-Sleep -Seconds 5

Write-Host "App pool state after start:"
Get-IISAppPool -Name $AppPoolName | Select-Object Name, State

Start-Web-Site($SiteName)

Write-Host "Sleeping 5 seconds after site start"
Start-Sleep -Seconds 5

Write-Host "Site state after start:"
Get-WebsiteState -Name $SiteName


# ------------------------------------------------------------------------------
# Final health check — confirm site and pool are both running
# ------------------------------------------------------------------------------

Write-Host ""
Write-Host "--- Final status check ---"

$poolState = (Get-WebAppPoolState -Name $AppPoolName).Value
$siteState = (Get-WebsiteState    -Name $SiteName).Value

Write-Host "App pool '$AppPoolName' : $poolState"
Write-Host "Site      '$SiteName'   : $siteState"

if ($poolState -ne "Started" -or $siteState -ne "Started") {
    Write-Error "Deployment incomplete — site or app pool did not reach Started state."
    Exit 1
}

Write-Host ""
Write-Host "============================================================"
Write-Host "  Environment Deploy Complete -- $SurgeEnvName"
Write-Host "============================================================"
Exit 0
