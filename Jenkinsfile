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

    & (Join-Path ($PSHOME -replace "SysWOW64", "SysNative") powershell.exe) -File `
        (Join-Path $PSScriptRoot $MyInvocation.MyCommand) @args

    Exit $LastExitCode
}

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
$SurgeApiPath          = "{SURGE_API_PATH}"

$AppPoolName = "ETarApiService-SBX"
$SiteName    = "ETarApiService-SBX"
$appcmd      = "$env:SystemRoot\system32\inetsrv\appcmd.exe"

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
# Validate appcmd.exe exists
# ---------------------------------------------------------------------------

if (-not (Test-Path $appcmd)) {
    Write-Error "appcmd.exe not found at: $appcmd"
    Exit 1
}
Write-Host "appcmd.exe found at: $appcmd"

# ---------------------------------------------------------------------------
# Verify the site and app pool exist
# ---------------------------------------------------------------------------

if (-not (Test-Path "IIS:\Sites\$SiteName")) {
    Write-Error "IIS site '$SiteName' does not exist."
    Exit 1
}

if (-not (Test-Path "IIS:\AppPools\$AppPoolName")) {
    Write-Error "IIS app pool '$AppPoolName' does not exist."
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
# w3wp.exe can keep applicationHost.config locked even after pool stops
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
# Unlock environmentVariables configuration section
# ---------------------------------------------------------------------------

Write-Host "--- Unlocking environmentVariables configuration section ---"

try {
    Set-WebConfiguration `
        -Filter "system.applicationHost/applicationPools" `
        -PSPath "MACHINE/WEBROOT" `
        -Metadata overrideMode `
        -Value Allow
    Write-Host "Configuration section unlocked successfully"
}
catch {
    Write-Warning "Unlock warning (non-fatal): $_"
}

# ---------------------------------------------------------------------------
# Apply environment variables using appcmd.exe
# Proven approach on IIS 10 / Windows Server 2025
#
# >>> CHANGED SECTION STARTS HERE <
# The old approach did a "clear all, then add all" using an invalid
# appcmd form ("set apppool <name> /environmentVariables"), which
# silently failed and left stale entries in place -> redeploys hit
# "Cannot add duplicate collection entry ... 'SURGE_ENVNAME'" etc.
# Fix: remove each key immediately before re-adding it, inside the loop,
# so the script is idempotent and safe to run on every deploy.
# ---------------------------------------------------------------------------

Write-Host "--- Applying environment variables via appcmd.exe ---"

# Format RPM root path - convert forward slashes to backslashes
$formattedRpmRoot = $SurgeRpmRoot -replace '/', [char]92

# Build env vars hashtable
$envVars = @{
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

# Remove-then-add each environment variable (idempotent)
$envVars.GetEnumerator() | ForEach-Object {
    $name  = $_.Key
    $value = $_.Value

    # NEW: remove existing entry for this key first, if present.
    # This is what makes the script safe to re-run on every deploy.
    & $appcmd set config `
        -section:system.applicationHost/applicationPools `
        /-"[name='$AppPoolName'].environmentVariables.[name='$name']" `
        /commit:apphost 2>$null | Out-Null

    & $appcmd set config `
        -section:system.applicationHost/applicationPools `
        /+"[name='$AppPoolName'].environmentVariables.[name='$name',value='$value']" `
        /commit:apphost

    if ($LASTEXITCODE -eq 0) {
        if ($name -match "ROLE_ID|SECRET_ID") {
            Write-Host "    Set: $name = ****"
        } else {
            Write-Host "    Set: $name = $value"
        }
    } else {
        Write-Error "Failed to set environment variable: $name (exit code $LASTEXITCODE)"
        Exit 1
    }
}

# >>> CHANGED SECTION ENDS HERE <

Write-Host "All environment variables applied via appcmd.exe"

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

Write-Host "Environment Deploy Complete"
Exit 0
