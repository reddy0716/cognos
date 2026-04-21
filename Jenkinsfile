Write-Host "Setting logging directory for '$SiteName' (site-specific)"
Set-ItemProperty "IIS:\Sites\$SiteName" -Name logFile.directory -Value $LoggingDir

Write-Host "Installing/Updating SBX-specific Datadog Configuration"

$DatadogTarget = "C:\ProgramData\Datadog\conf.d\etarweb_sbx.d"

if (-Not (Test-Path $DatadogTarget)) {
    New-Item -ItemType Directory -Path $DatadogTarget | Out-Null
}

xcopy /s /y /e "$StagingDir\serverconfig\datadog\conf.d\etarweb_sbx.d\*" "$DatadogTarget\"


