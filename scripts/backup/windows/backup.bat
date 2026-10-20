@echo off
setlocal
title WildBar POS - Database Backup

echo ========================================================
echo         WildBar POS - 1-Click Database Backup
echo ========================================================
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0backup.ps1" -TriggerReason manual

echo.
echo ========================================================
echo Backup process complete.
echo ========================================================
echo.
pause
