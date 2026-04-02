function Stop-Web-App-Pool($AppPoolName) {
     if ((Get-WebAppPoolState -Name $AppPoolName).Value -eq "Stopped") {
         Write-Host "$AppPoolName already stopped"
     }
     else {
         Write-Host "Shutting down $AppPoolName"
         Stop-WebAppPool -Name $AppPoolName
     }
 
     do {
         Start-Sleep -Seconds 1
     } until ((Get-WebAppPoolState -Name $AppPoolName).Value -eq "Stopped")
 }
 
 function Stop-Web-Site($WebsiteName) {
     if ((Get-WebsiteState -Name $WebsiteName).Value -eq "Stopped") {
         Write-Host "$WebsiteName already stopped"
     }
     else {
         Write-Host "Shutting down $WebsiteName"
         Stop-Website -Name $WebsiteName
     }
 
     do {
         Start-Sleep -Seconds 1
     } until ((Get-WebsiteState -Name $WebsiteName).Value -eq "Stopped")
 }
 
 # Ensure script runs in 64-bit mode
 if ($PSHOME -like "*SysWOW64*") {
     Write-Warning "Restarting script in 64-bit Windows PowerShell..."
     & (Join-Path ($PSHOME -replace "SysWOW64", "SysNative") powershell.exe) -NoProfile -File `
         (Join-Path $PSScriptRoot $MyInvocation.MyCommand) @args
     Exit $LastExitCode
 }
 
 # Setup Paths & Variables
 Import-Module -Name WebAdministration
 $SiteName = "Apiservices-SBX"
 $SiteFolder = 'E:\inetpub\ApiServices'
 $LoggingDir = 'E:\IISLogs'
 $AppPoolName = 'Apiservices-SBX'
 $StagingDir = "E:\tar-surge-Api-staging"
 $IISRootDir = "E:\inetpub"
 $BindingProtocol = "http"
 $BindingPort     = 8081
 

 
 # Ensure Required Directories Exist
 Write-Host "Ensuring required directories exist..."
 @("$SiteFolder", "$LoggingDir", "E:\apps\ErrorLogs") | ForEach-Object {
     if (-Not (Test-Path -Path $_)) {
         New-Item -ItemType "directory" -Path $_ | Out-Null
     }
 }

# Stop Site & App Pool if they exist
 Write-Host "Stopping '$SiteName'"
 Stop-Web-Site("$SiteName")
 Write-Host "Stopping Application Pool '$AppPoolName'"
 Stop-Web-App-Pool("$AppPoolName")

# Remove Existing Site & App Pool
Write-Host "Removing '$SiteName' from IIS"
Remove-Website -Name "$SiteName" -ErrorAction SilentlyContinue

Write-Host "Removing Application Pool '$AppPoolName'"
Remove-WebAppPool -Name "$AppPoolName" -ErrorAction SilentlyContinue

# Create New Application Pool
Write-Host "Creating Application Pool '$AppPoolName'"
New-WebAppPool -Name $AppPoolName

# Create IIS Site (No Nested App)
Write-Host "Creating IIS site '$SiteName' and assigning to App Pool '$AppPoolName'"
New-WebSite -Name "$SiteName" -PhysicalPath "$SiteFolder" -ApplicationPool "$AppPoolName" -Force

Get-WebBinding -Name "$SiteName" -ErrorAction SilentlyContinue | Remove-WebBinding -ErrorAction SilentlyContinue
New-WebBinding -Name "$SiteName" -Protocol $BindingProtocol -Port $BindingPort
 
# Configure Logging Directory
Write-Host "Setting logging directory only for site '$SiteName'"
Set-ItemProperty "IIS:\Sites\$SiteName" -Name logFile.directory -Value $LoggingDir
 
Write-Host "Setting $AppPoolName to No Managed Code"
Set-ItemProperty "IIS:\AppPools\$AppPoolName" -Name managedRuntimeVersion -Value ""

# ================================
# SET IIS RECYCLE TIME (12:45 AM PST → 08:45 UTC)
# ================================

$RecycleTime = "08:45"

Write-Host "Clearing existing recycle schedule for '$AppPoolName'..."

# Clear all existing schedule entries (works on all IIS versions)
Clear-WebConfiguration -Filter "system.applicationHost/applicationPools/add[@name='$AppPoolName']/recycling/periodicRestart/schedule"

Write-Host "Adding new recycle time $RecycleTime..."

# Add the new specific recycle time
Add-WebConfigurationProperty -pspath 'MACHINE/WEBROOT/APPHOST' `
  -filter "system.applicationHost/applicationPools/add[@name='$AppPoolName']/recycling/periodicRestart/schedule" `
  -name "." -value @{value=$RecycleTime}

Write-Host "Recycle schedule updated successfully to $RecycleTime UTC (12:15 AM PST)"

# === Grant App Pool permission to zConnect cert using Subject ===

$appPoolIdentity = "IIS AppPool\Apiservices-SBX"

# Search for certificate by subject (Common Name)
$cert = Get-ChildItem -Path Cert:\LocalMachine\My | Where-Object {
    $_.Subject -like '*zOSConnect*Client*'
}

if (-not $cert) {
    Write-Error "zConnect certificate with CN 'MMIS Surge zOSConnect Client' not found."
    exit 1
}

Write-Host "Found zConnect certificate: $($cert.Subject)"

# Locate the private key file path
$keyFile = Join-Path "$env:ProgramData\Microsoft\Crypto\RSA\MachineKeys" `
    $cert.PrivateKey.CspKeyContainerInfo.UniqueKeyContainerName

# Grant Read permission if not already present
$acl = Get-Acl $keyFile
if (-not ($acl.Access | Where-Object { $_.IdentityReference -eq $appPoolIdentity })) {
    $rule = New-Object System.Security.AccessControl.FileSystemAccessRule($appPoolIdentity, "Read", "Allow")
    $acl.AddAccessRule($rule)
    Set-Acl $keyFile $acl
    Write-Host "Read access granted to $appPoolIdentity on zConnect cert"
} else {
    Write-Host "$appPoolIdentity already has read access to zConnect cert"
}


# Disable time-based recycling (default is 1740 minutes)
Write-Host "Disabling regular time interval recycling for $AppPoolName"
Set-WebConfigurationProperty -Filter "/system.applicationHost/applicationPools/add[@name='$AppPoolName']/recycling/periodicRestart" -Name "time" -Value "00:00:00"

# Enable 'Load User Profile'
Write-Host "Setting 'Load User Profile' to True for $AppPoolName"
Set-ItemProperty "IIS:\AppPools\$AppPoolName" -Name processModel.loadUserProfile -Value $true

# increase IIS size to 120MB
#Set-WebConfigurationProperty -pspath 'MACHINE/WEBROOT/APPHOST' `
#-filter "system.webServer/security/requestFiltering/requestLimits" `
#-name "maxAllowedContentLength" -value 125829120

# increase iis buffering
#Set-WebConfigurationProperty -pspath 'MACHINE/WEBROOT/APPHOST' `
#-filter "system.webServer/serverRuntime" `
#-name "uploadReadAheadSize" -value 131072

Write-Host "Pushing the index.html file out"
if (Test-Path "$IISRootDir\index.html") {
    Remove-Item "$IISRootDir\index.html"
}
Copy-Item $StagingDir\serverconfig\index.html -Destination $IISRootDir
$HOST_NAME = & hostname
(Get-Content $IISRootDir\index.html) -replace "{server-hostname}", "$HOST_NAME" | Set-Content $IISRootDir\index.html

Write-Host "Installing/Updating Datadog Configuration"

$DatadogTarget = "C:\ProgramData\Datadog\conf.d\surge_sbx.d"

Write-Host "Installing/Updating SBX-specific Datadog configuration"
if (-Not (Test-Path $DatadogTarget)) {
    New-Item -ItemType Directory -Path $DatadogTarget | Out-Null
}

xcopy /s /y /e "$StagingDir\serverconfig\datadog\conf.d\surge_sbx.d\*" "$DatadogTarget\"

Write-Host "`nAdding ddagentuser to C:\ProgramData\Amazon\CodeDeploy\deployment-logs so Datadog can read the CodeDeploy log file`n"
$Folder = 'C:\ProgramData\Amazon\CodeDeploy\deployment-logs'
$ACL = Get-Acl $Folder
$ACL_Rule = new-object System.Security.AccessControl.FileSystemAccessRule (
    'ddagentuser',
    'ReadAndExecute',
    ([System.Security.AccessControl.InheritanceFlags]::ContainerInherit -bor [System.Security.AccessControl.InheritanceFlags]::Objectinherit),
    [System.Security.AccessControl.PropagationFlags]::None,
    [System.Security.AccessControl.AccessControlType]::Allow
)
$ACL.SetAccessRule($ACL_Rule)
Set-Acl -Path $Folder -AclObject $ACL

Write-Host "Restarting the Datadog agent service"
& 'C:\Program Files\Datadog\Datadog Agent\bin\agent.exe' restart-service


Write-Host "Configuration Deploy Complete"
