$RecycleTime = "00:15"   # 12:15 AM PST

Write-Host "Configuring IIS recycle schedule for AppPool '$AppPoolName' at $RecycleTime"

# Clear existing schedule
Set-ItemProperty "IIS:\AppPools\$AppPoolName" -Name recycling.periodicRestart.schedule -Value @()

# Add the new scheduled recycle time
Add-WebConfigurationProperty -pspath 'MACHINE/WEBROOT/APPHOST' `
  -filter "system.applicationHost/applicationPools/add[@name='$AppPoolName']/recycling/periodicRestart/schedule" `
  -name "." -value @{value=$RecycleTime}

Write-Host "Recycle Scheduled Successfully at $RecycleTime"
