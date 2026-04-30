function Stop-Web-App-Pool($AppPoolName) {
    if ( (Get-WebAppPoolState -Name $AppPoolName).Value -eq "Stopped" ) {
        Write-Host $AppPoolName " already stopped"
    }
    else {
        Write-Host "Shutting down the " $AppPoolName
        Write-Host "    $AppPoolName status: " (Get-WebAppPoolState $AppPoolName).Value
        Stop-WebAppPool -Name $AppPoolName
    }
    do {
        Write-Host "    $AppPoolName status: " (Get-WebAppPoolState $AppPoolName).Value
        Start-Sleep -Seconds 1
    }
    until ( (Get-WebAppPoolState -Name $AppPoolName).Value -eq "Stopped" )
    Write-Host $AppPoolName " stopped successfully"
}

function Start-Web-App-Pool($AppPoolName) {
    if ( (Get-WebAppPoolState -Name $AppPoolName).Value -eq "Started" ) {
        Write-Host $AppPoolName " already started"
    }
    else {
        Write-Host "Starting up " $AppPoolName
        Write-Host "    $AppPoolName status: " (Get-WebAppPoolState $AppPoolName).Value
        Start-WebAppPool -Name $AppPoolName
    }
    do {
        Write-Host "    $AppPoolName status: " (Get-WebAppPoolState $AppPoolName).Value
        Start-Sleep -Seconds 1
    }
    until ( (Get-WebAppPoolState -Name $AppPoolName).Value -eq "Started" )
    Write-Host $AppPoolName " started successfully"
}

function Stop-Web-Site($WebsiteName) {
    if ( (Get-WebsiteState -Name $WebsiteName).Value -eq "Stopped" ) {
        Write-Host $WebsiteName " already stopped"
    }
    else {
        Write-Host "Shutting down the " $WebsiteName
        Write-Host "    $WebsiteName status: " (Get-WebsiteState $WebsiteName).Value
        Stop-Website -Name $WebsiteName
    }
    do {
        Write-Host "    $WebsiteName status: " (Get-WebsiteState $WebsiteName).Value
        Start-Sleep -Seconds 1
    }
    until ( (Get-WebsiteState -Name $WebsiteName).Value -eq "Stopped" )
    Write-Host $WebsiteName " stopped successfully"
}

function Start-Web-Site($WebsiteName) {
    if ( (Get-WebsiteState -Name $WebsiteName).Value -eq "Started" ) {
        Write-Host $WebsiteName " already started"
    }
    else {
        Write-Host "Starting up " $WebsiteName
        Write-Host "    $WebsiteName status: " (Get-WebsiteState $WebsiteName).Value
        Start-Website -Name $WebsiteName
    }
    do {
        Write-Host "    $WebsiteName status: " (Get-WebsiteState $WebsiteName).Value
        Start-Sleep -Seconds 1
    }
    until ( (Get-WebsiteState -Name $WebsiteName).Value -eq "Started" )
    Write-Host $WebsiteName " started successfully"
}

# This is needed because AWS CodeDeploy Agent runs in 32-bit mode,
# script below needs to run in 64-bit mode.
# Are you running in 32-bit mode?
#   (\SysWOW64\ = 32-bit mode)

if ($PSHOME -like "*SysWOW64*")
{
    Write-Warning "Restarting this script under 64-bit Windows PowerShell."

    # Restart this script under 64-bit Windows PowerShell.
    #   (\SysNative\ redirects to \System32\ for 64-bit mode)

    & (Join-Path ($PSHOME -replace "SysWOW64", "SysNative") powershell.exe) -File `
        (Join-Path $PSScriptRoot $MyInvocation.MyCommand) @args

    # Exit 32-bit script.
    Exit $LastExitCode
}

# Was restart successful?
Write-Warning "Hello from $PSHOME"
Write-Warning "  (\SysWOW64\ = 32-bit mode, \System32\ = 64-bit mode)"
Write-Warning "Original arguments (if any): $args"

# ---------------------------------------------------------------------------
# Tokenized variables - replaced by Jenkins sed during Prepare Deployment
# ---------------------------------------------------------------------------

$VaultAddress          = "{VAULT_ADDR}"
$VaultAppRoleRoleId    = "{APPROLE_ROLE_ID}"
$VaultAppRoleSecretId  = "{APPROLE_SECRET_ID}"
$VaultSecretPath       = "{VAULT_SECRET_PATH}"
$VaultSecretPathLtar   = "{VAULT_SECRET_PATH_LTAR}"
$VaultSecretPathImgVwr = "{VAULT_SECRET_PATH_IMGVWR}"
$VaultAppRoleAuthPath  = "{VAULT_APPROLE_AUTH_PATH}"
$SurgeEnvName          = "{SURGE_ENVNAME}"
$SurgeRpmRoot          = "{SURGE_RPM_ROOT}"

# ---------------------------------------------------------------------------
# IIS site and app pool names - hardcoded to match actual IIS configuration
# ---------------------------------------------------------------------------

$AppPoolName = "Apiservices-SBX"
$SiteName    = "Apiservices-SBX"

Write-Host "Environment  : $SurgeEnvName"
Write-Host "IIS Site     : $SiteName"
Write-Host "App Pool     : $AppPoolName"
Write-Host "Vault Addr   : $VaultAddress"
Write-Host "RPM Root     : $SurgeRpmRoot"

# ---------------------------------------------------------------------------
# Validate IIS module is available before proceeding
# ---------------------------------------------------------------------------

if (-not (Get-Module -ListAvailable -Name WebAdministration)) {
    Write-Error "WebAdministration module not found. Ensure IIS is installed."
    Exit 1
}
Import-Module WebAdministration
Write-Host "WebAdministration module loaded"

# ---------------------------------------------------------------------------
# Verify the site and app pool exist before attempting to stop them
# ---------------------------------------------------------------------------

if (-not (Test-Path "IIS:\Sites\$SiteName")) {
    Write-Error "IIS site '$SiteName' does not exist. Verify IIS configuration."
    Exit 1
}

if (-not (Test-Path "IIS:\AppPools\$AppPoolName")) {
    Write-Error "IIS app pool '$AppPoolName' does not exist. Verify IIS configuration."
    Exit 1
}

# ---------------------------------------------------------------------------
# Stop site and app pool
# ---------------------------------------------------------------------------

Write-Host "--- Stopping IIS site and app pool ---"

Stop-Web-Site($SiteName)

Write-Host "Sleeping 5 seconds after site stop"
Start-Sleep -Seconds 5

Stop-Web-App-Pool($AppPoolName)

Write-Host "Sleeping 5 seconds after app pool stop"
Start-Sleep -Seconds 5

Write-Host "App pool state after stop:"
Get-IISAppPool -Name $AppPoolName | Select-Object Name, State

# ---------------------------------------------------------------------------
# Kill any lingering w3wp.exe worker processes
# w3wp.exe can keep applicationHost.config locked even after the pool shows
# as Stopped, causing ServerManager to hang indefinitely.
# ---------------------------------------------------------------------------

Write-Host "--- Checking for lingering w3wp.exe worker processes ---"

$workers = Get-Process -Name "w3wp" -ErrorAction SilentlyContinue
if ($workers) {
    $workers | ForEach-Object {
        Write-Host "Killing w3wp.exe PID: $($_.Id)"
        Stop-Process -Id $_.Id -Force
    }
    Write-Host "Sleeping 3 seconds after killing worker processes"
    Start-Sleep -Seconds 3
    Write-Host "Worker processes cleared"
} else {
    Write-Host "No lingering w3wp.exe processes found"
}

# ---------------------------------------------------------------------------
# Build the environment variable hashtable
# ---------------------------------------------------------------------------

$envVars = @{
    VAULT_ADDRESS            = $VaultAddress
    VAULT_APPROLE_ROLE_ID    = $VaultAppRoleRoleId
    VAULT_APPROLE_SECRET_ID  = $VaultAppRoleSecretId
    VAULT_SECRET_PATH        = $VaultSecretPath
    VAULT_SECRET_PATH_LTAR   = $VaultSecretPathLtar
    VAULT_SECRET_PATH_IMGVWR = $VaultSecretPathImgVwr
    VAULT_APPROLE_AUTH_PATH  = $VaultAppRoleAuthPath
    SURGE_ENVNAME            = $SurgeEnvName
    SURGE_RPM_ROOT           = $SurgeRpmRoot
    SURGE_RPM_ONLINE_KEY     = "/online"
    DD_LOGS_ENABLED          = "true"
}

# ---------------------------------------------------------------------------
# Apply environment variables via Microsoft.Web.Administration API
# - DLL loaded by explicit path (not GAC) - required on Windows Server 2025
# - ServerManager constructed with explicit applicationHost.config path
#   to ensure correct config file is read on this server
# ---------------------------------------------------------------------------

Write-Host "--- Applying environment variables via Microsoft.Web.Administration API ---"

$mwaPath    = "$env:SystemRoot\system32\inetsrv\Microsoft.Web.Administration.dll"
$configPath = "$env:SystemRoot\system32\inetsrv\config\applicationHost.config"

if (-not (Test-Path $mwaPath)) {
    Write-Error "Microsoft.Web.Administration.dll not found at: $mwaPath"
    Exit 1
}

if (-not (Test-Path $configPath)) {
    Write-Error "applicationHost.config not found at: $configPath"
    Exit 1
}

Write-Host "Loading DLL from       : $mwaPath"
Write-Host "Using config file from : $configPath"

try {
    Add-Type -Path $mwaPath

    $serverManager = New-Object Microsoft.Web.Administration.ServerManager($configPath)
    $pool = $serverManager.ApplicationPools[$AppPoolName]

    if ($null -eq $pool) {
        Write-Error "ServerManager could not find app pool '$AppPoolName' in $configPath"
        Write-Host "Available app pools:"
        $serverManager.ApplicationPools | ForEach-Object { Write-Host "    $($_.Name)" }
        Exit 1
    }

    Write-Host "Found app pool: $($pool.Name)"

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

# ---------------------------------------------------------------------------
# Verify env vars were written correctly by reading back from IIS
# ---------------------------------------------------------------------------

Write-Host "--- Verifying environment variables written to app pool ---"

try {
    $verifyManager = New-Object Microsoft.Web.Administration.ServerManager($configPath)
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

# ---------------------------------------------------------------------------
# Start app pool and site
# ---------------------------------------------------------------------------

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

# ---------------------------------------------------------------------------
# Final health check
# ---------------------------------------------------------------------------

Write-Host "--- Final status check ---"

$poolState = (Get-WebAppPoolState -Name $AppPoolName).Value
$siteState = (Get-WebsiteState    -Name $SiteName).Value

Write-Host "App pool '$AppPoolName' : $poolState"
Write-Host "Site     '$SiteName'    : $siteState"

if ($poolState -ne "Started" -or $siteState -ne "Started") {
    Write-Error "Deployment incomplete - site or app pool did not reach Started state."
    Exit 1
}

Write-Host "Environment Deploy Complete - $SurgeEnvName"
Exit 0
