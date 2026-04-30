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
}

# This is needed because AWS CodeDeploy Agent runs in 32-bit mode,
# this script needs to run in 64-bit mode.
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
# Tokenized variables — injected by Jenkins sed commands during Prepare stage
#
# Token                    Jenkinsfile source
# -------                  -----------------
# {VAULT_ADDR}             VAULT_ADDR[SURGE_ENV]
# {APPROLE_ROLE_ID}        Jenkins credential: APPROLE_ROLE_ID (non-PRD)
# {APPROLE_SECRET_ID}      Jenkins credential: APPROLE_SECRET_ID (non-PRD)
# {VAULT_SECRET_PATH}      VAULT_SECRET_PATH[SURGE_ENV]
# {VAULT_SECRET_PATH_LTAR} VAULT_SECRET_PATH_LTAR[SURGE_ENV]
# {VAULT_SECRET_PATH_IMGVWR} VAULT_SECRET_PATH_IMGVWR[SURGE_ENV]
# {VAULT_APPROLE_AUTH_PATH} VAULT_APPROLE_AUTH_PATH (scalar)
# {SURGE_ENVNAME}          SURGE_ENV_CONFIG[SURGE_ENV]["SURGE_ENVNAME"]  ← fix Bug 2 in Jenkinsfile
# {SURGE_RPM_ROOT}         SURGE_ENV_CONFIG[SURGE_ENV]["SURGE_RPM_ROOT"] ← fix Bug 1 in Jenkinsfile
# {SURGE_API_PATH}         SURGE_API_PATH[SURGE_ENV]
# ---------------------------------------------------------------------------

$VaultAddress            = "{VAULT_ADDR}"
$VaultAppRoleRoleId      = "{APPROLE_ROLE_ID}"
$VaultAppRoleSecretId    = "{APPROLE_SECRET_ID}"
$VaultSecretPath         = "{VAULT_SECRET_PATH}"
$VaultSecretPathLtar     = "{VAULT_SECRET_PATH_LTAR}"
$VaultSecretPathImgVwr   = "{VAULT_SECRET_PATH_IMGVWR}"
$VaultAppRoleAuthPath    = "{VAULT_APPROLE_AUTH_PATH}"
$SurgeEnvName            = "{SURGE_ENVNAME}"
$SurgeRpmRoot            = "{SURGE_RPM_ROOT}"
$SurgeApiPath            = "{SURGE_API_PATH}"

# ---------------------------------------------------------------------------
# IIS site and app pool names are derived from the environment token so this
# script works correctly for both SANDBOX and HOTFIX without modification.
# ---------------------------------------------------------------------------
$AppPoolName = "Apiservices-$SurgeEnvName"
$SiteName    = "Apiservices-$SurgeEnvName"

# ---------------------------------------------------------------------------
# Stop site and app pool before applying changes
# ---------------------------------------------------------------------------
Write-Host "Stopping website: $SiteName"
Stop-Web-Site($SiteName)
Write-Host "Stop website status: $?"

Write-Host "Sleeping 5 seconds for website to stop"
Start-Sleep -Seconds 5

Write-Host "Stopping Application Pool: $AppPoolName"
Stop-Web-App-Pool($AppPoolName)

Write-Host "Sleeping 5 seconds for app pool to stop"
Start-Sleep -Seconds 5

Write-Host "Current status of Application Pool"
Get-IISAppPool -Name $AppPoolName

# ---------------------------------------------------------------------------
# Apply environment variables to the IIS App Pool
# ---------------------------------------------------------------------------
Write-Host "Setting App Pool environment variables for $AppPoolName"

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
    SURGE_API_PATH             = $SurgeApiPath
    SURGE_RPM_ONLINE_KEY       = "/online"
    DD_LOGS_ENABLED            = "true"
}

Set-ItemProperty "IIS:\AppPools\$AppPoolName" `
  -Name processModel.environmentVariables `
  -Value $envVars

Write-Host "App Pool environment variables applied successfully"

# ---------------------------------------------------------------------------
# Start app pool and site
# ---------------------------------------------------------------------------
Write-Host "Starting Application Pool: $AppPoolName"
Start-WebAppPool -Name $AppPoolName

Write-Host "Sleeping 5 seconds for app pool to start"
Start-Sleep -Seconds 5

Write-Host "Current status of Application Pool"
Get-IISAppPool -Name $AppPoolName

Write-Host "Starting website: $SiteName"
Start-Website -Name $SiteName
Write-Host "Start website status: $?"

Write-Host "Environment Deploy Complete"
