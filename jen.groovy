Get-ChildItem IIS:\AppPools | ForEach-Object {
    Write-Host "Removing app pool: $($_.Name)"
    Remove-WebAppPool -Name $_.Name -ErrorAction SilentlyContinue
}


Clear-WebConfiguration -Filter "system.applicationHost/applicationPools/add[@name='$AppPoolName']/recycling/periodicRestart/schedule"


# ================================
# SET IIS RECYCLE TIME (12:15 AM PST → 08:15 UTC)
# ================================

$RecycleTime = "08:15"

Write-Host "Clearing existing recycle schedule for '$AppPoolName'..."

# Clear all existing schedule entries (works on all IIS versions)
Clear-WebConfiguration -Filter "system.applicationHost/applicationPools/add[@name='$AppPoolName']/recycling/periodicRestart/schedule"

Write-Host "Adding new recycle time $RecycleTime..."

# Add the new specific recycle time
Add-WebConfigurationProperty -pspath 'MACHINE/WEBROOT/APPHOST' `
  -filter "system.applicationHost/applicationPools/add[@name='$AppPoolName']/recycling/periodicRestart/schedule" `
  -name "." -value @{value=$RecycleTime}

Write-Host "Recycle schedule updated successfully to $RecycleTime UTC (12:15 AM PST)"
