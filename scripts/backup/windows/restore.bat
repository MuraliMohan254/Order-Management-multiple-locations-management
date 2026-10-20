@echo off
setlocal
title WildBar POS - Database Restore

echo ========================================================
echo         WildBar POS - 1-Click Database Restore
echo ========================================================
echo WARNING: This will restore the database from the latest
echo backup file found in your configured backup directory.
echo.
set /p CONFIRM="Are you sure you want to proceed? (Y/N): "
if /i not "%CONFIRM%"=="Y" (
    echo Restore cancelled by user.
    pause
    exit /b 0
)

echo.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0restore.ps1"

echo.
echo ========================================================
echo Restore process complete.
echo ========================================================
echo.
pause
