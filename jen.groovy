REM === Cleanup: remove EVERYTHING except the ZIP and Version.TXT ===
echo Cleaning up D:\SurgeUpdate but keeping ZIP + Version.TXT...

for %%F in ("%folder%\*") do (
    REM Skip ZIPs
    echo %%~nxF | findstr /I "SurgeUpdate_.*.ZIP" >nul
    if not !errorlevel! == 0 (
        REM Skip Version.TXT
        if /I not "%%~nxF"=="Version.TXT" (
            echo Deleting: %%F
            del /q "%%F" 2>nul
            rmdir /s /q "%%F" 2>nul
        )
    )
)

echo Cleanup complete.
