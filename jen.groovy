$RecycleTime = "00:15"   # 12:15 AM PST

Write-Host "Configuring IIS recycle schedule for AppPool '$AppPoolName' at $RecycleTime"

# Clear existing schedule
Set-ItemProperty "IIS:\AppPools\$AppPoolName" -Name recycling.periodicRestart.schedule -Value @()

# Add the new scheduled recycle time
Add-WebConfigurationProperty -pspath 'MACHINE/WEBROOT/APPHOST' `
  -filter "system.applicationHost/applicationPools/add[@name='$AppPoolName']/recycling/periodicRestart/schedule" `
  -name "." -value @{value=$RecycleTime}

Write-Host "Recycle Scheduled Successfully at $RecycleTime"


# ================================
# SET IIS RECYCLE TIME (12:15 AM PST → 08:15 UTC)
# ================================

$RecycleTime = "08:15"   # Correct UTC time for 12:15 AM PST

Write-Host "Clearing all existing recycle times for AppPool '$AppPoolName'..."

# Completely remove ALL old schedule entries (7:16 PM etc.)
Clear-WebConfigurationSection -pspath 'MACHINE/WEBROOT/APPHOST' `
  -filter "system.applicationHost/applicationPools/add[@name='$AppPoolName']/recycling/periodicRestart/schedule"

Write-Host "Adding new recycle time $RecycleTime UTC..."

# Add only the new scheduled recycle time
Add-WebConfigurationProperty -pspath 'MACHINE/WEBROOT/APPHOST' `
  -filter "system.applicationHost/applicationPools/add[@name='$AppPoolName']/recycling/periodicRestart/schedule" `
  -name "." -value @{value=$RecycleTime}

Write-Host "Recycle schedule successfully set to $RecycleTime UTC (12:15 AM PST)"
