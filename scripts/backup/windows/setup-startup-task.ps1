<#
.SYNOPSIS
    Registers a Windows Scheduled Task that triggers backup on User Logon / System Startup with Catch-Up.
.DESCRIPTION
    Ideal for Standalone POS terminals where machines are powered down at night.
#>

param(
    [string]$TaskName = "WildBar_POS_Startup_Backup"
)

$scriptPath = (Resolve-Path (Join-Path $PSScriptRoot "backup.ps1")).Path

Write-Host "Registering Windows Scheduled Task '$TaskName' to run at User Logon with Catch-Up..."

try {
    $action = New-ScheduledTaskAction -Execute "powershell.exe" -Argument "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$scriptPath`" -TriggerReason logon"
    $trigger = New-ScheduledTaskTrigger -AtLogOn
    $settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable

    Register-ScheduledTask -TaskName $TaskName -Action $action -Trigger $trigger -Settings $settings -Description "WildBar POS Automated Backup on Startup/Logon" -Force | Out-Null

    Write-Host "[OK] Windows Scheduled Task '$TaskName' registered successfully." -ForegroundColor Green
    Write-Host "Backups will automatically run whenever the POS terminal powers on / cashier logs in."
} catch {
    Write-Warning "Could not register task using PowerShell cmdlets, attempting schtasks.exe fallback..."
    $cmd = "schtasks /create /tn `"$TaskName`" /tr `"powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File \""$scriptPath\"" -TriggerReason logon`" /sc onlogon /f"
    Invoke-Expression $cmd
}
