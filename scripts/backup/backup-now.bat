@echo off
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0windows\backup.ps1" -TriggerReason manual
echo.
pause
