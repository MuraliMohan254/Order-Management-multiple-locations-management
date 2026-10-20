<#
.SYNOPSIS
    Production-Grade PostgreSQL Database Restore Script for Windows (Standalone POS).
.DESCRIPTION
    - Reads settings dynamically from backup-config.json
    - Restores from the newest .dump file or a user-specified file
    - Safely disconnects all sessions using DROP DATABASE WITH (FORCE)
    - Recreates clean database and restores full schema & data using pg_restore.exe
#>

param(
    [string]$BackupFilePath = "",
    [string]$ConfigPath = ""
)

$ErrorActionPreference = "Continue"

# 1. Locate and load backup-config.json
if (-not $ConfigPath -or -not (Test-Path $ConfigPath)) {
    $searchPaths = @(
        (Join-Path $PSScriptRoot "backup-config.json"),
        (Join-Path $PSScriptRoot "..\backup-config.json"),
        (Join-Path $PSScriptRoot "..\..\backup-config.json"),
        "D:\Projects\Commercial Projects\WildBar\Order Management\backup-config.json"
    )
    foreach ($p in $searchPaths) {
        if (Test-Path $p) {
            $ConfigPath = (Resolve-Path $p).Path
            break
        }
    }
}

$config = $null
if ($ConfigPath -and (Test-Path $ConfigPath)) {
    try {
        $rawJson = Get-Content -Path $ConfigPath -Raw -Encoding utf8
        $config = $rawJson | ConvertFrom-Json
    } catch {
        $errMsg = $_.Exception.Message
        Write-Warning "Could not parse $ConfigPath - $errMsg"
    }
}

$dbHost = if ($config.database.host) { $config.database.host } else { "localhost" }
$dbPort = if ($config.database.port) { [int]$config.database.port } else { 5432 }
$dbName = if ($config.database.name) { $config.database.name } else { "order_management" }
$dbUser = if ($config.database.username) { $config.database.username } else { "postgres" }

# Resolve Database Password securely (Windows DPAPI)
$dbPassword = ""
if ($config.database.encryptedPassword) {
    try {
        $cleanHex = $config.database.encryptedPassword.Replace("enc:dpapi:", "").Trim()
        $sec = ConvertTo-SecureString $cleanHex
        $bstr = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec)
        $dbPassword = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($bstr)
        [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
    } catch {
        Write-Warning "Could not decrypt database password via DPAPI: $($_.Exception.Message)"
    }
}

if (-not $dbPassword -and $config.database.password) {
    $rawPlain = $config.database.password
    $sec = ConvertTo-SecureString $rawPlain -AsPlainText -Force
    $encCipher = "enc:dpapi:" + (ConvertFrom-SecureString $sec)
    if ($config.database.PSObject.Properties['encryptedPassword']) {
        $config.database.encryptedPassword = $encCipher
    } else {
        $config.database | Add-Member -NotePropertyName "encryptedPassword" -NotePropertyValue $encCipher -Force
    }
    $config.database.password = ""
    try {
        if ($ConfigPath -and (Test-Path $ConfigPath)) {
            $config | ConvertTo-Json -Depth 5 | Set-Content -Path $ConfigPath -Encoding utf8
        }
    } catch {}
    $dbPassword = $rawPlain
}

if (-not $dbPassword -and [Environment]::UserInteractive) {
    Write-Host "==========================================================" -ForegroundColor Yellow
    Write-Host "Initial Security Setup: No database password configured." -ForegroundColor Yellow
    $secureInput = Read-Host "PostgreSQL Password" -AsSecureString
    if ($secureInput) {
        $encCipher = "enc:dpapi:" + (ConvertFrom-SecureString $secureInput)
        if ($config.database.PSObject.Properties['encryptedPassword']) {
            $config.database.encryptedPassword = $encCipher
        } else {
            $config.database | Add-Member -NotePropertyName "encryptedPassword" -NotePropertyValue $encCipher -Force
        }
        $config.database.password = ""
        try {
            if ($ConfigPath -and (Test-Path $ConfigPath)) {
                $config | ConvertTo-Json -Depth 5 | Set-Content -Path $ConfigPath -Encoding utf8
            }
        } catch {}
        $bstr = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureInput)
        $dbPassword = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($bstr)
        [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
    }
}
$rawBackupDir = if ($config.standalone.backupDirectory) { $config.standalone.backupDirectory } else { "D:\WildBar_Backups" }
$rawFallbackDir = if ($config.standalone.fallbackDirectory) { $config.standalone.fallbackDirectory } else { "%USERPROFILE%\WildBar_Backups" }

function Write-RestoreLog {
    param([string]$Text, [string]$Level = "INFO")
    $now = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
    $line = "[$now] [$Level] $Text"
    Write-Host $line
}

Write-RestoreLog "=========================================================="
Write-RestoreLog "Starting WildBar POS Database Restore"

# 2. Find backup file across candidate directories
if (-not $BackupFilePath -or -not (Test-Path $BackupFilePath)) {
    $searchDirs = @()
    if ($rawBackupDir) { $searchDirs += [System.Environment]::ExpandEnvironmentVariables($rawBackupDir) }
    if ($rawFallbackDir) { $searchDirs += [System.Environment]::ExpandEnvironmentVariables($rawFallbackDir) }
    if ($env:USERPROFILE) { $searchDirs += (Join-Path $env:USERPROFILE "WildBar_Backups") }
    $searchDirs += (Join-Path ([System.IO.Path]::GetTempPath()) "WildBar_Backups")

    foreach ($dir in $searchDirs) {
        if (Test-Path $dir) {
            $latest = Get-ChildItem -Path $dir -Filter "wildbar_${dbName}_*.dump" | Where-Object { $_.Length -gt 0 } | Sort-Object LastWriteTime -Descending | Select-Object -First 1
            if ($latest) {
                $BackupFilePath = $latest.FullName
                Write-RestoreLog "Found latest backup in ${dir}: $($latest.Name) (Modified: $($latest.LastWriteTime))"
                break
            }
        }
    }
}

if (-not $BackupFilePath -or -not (Test-Path $BackupFilePath)) {
    Write-RestoreLog "ERROR: No valid backup file found in search locations." "ERROR"
    exit 1
}

Write-RestoreLog "Source File: $BackupFilePath"

# 3. Locate pg_restore and psql
$psqlPath = $null
$pgRestorePath = $null

$cmd = Get-Command "pg_restore.exe" -ErrorAction SilentlyContinue
if ($cmd) { $pgRestorePath = $cmd.Source }

$cmdPsql = Get-Command "psql.exe" -ErrorAction SilentlyContinue
if ($cmdPsql) { $psqlPath = $cmdPsql.Source }

if (-not $pgRestorePath -or -not $psqlPath) {
    $versions = @("17", "16", "15", "14", "13")
    foreach ($v in $versions) {
        $candidateRestore = "C:\Program Files\PostgreSQL\$v\bin\pg_restore.exe"
        $candidatePsql = "C:\Program Files\PostgreSQL\$v\bin\psql.exe"
        if ((Test-Path $candidateRestore) -and (Test-Path $candidatePsql)) {
            $pgRestorePath = $candidateRestore
            $psqlPath = $candidatePsql
            break
        }
    }
}

if (-not $pgRestorePath -or -not $psqlPath) {
    Write-RestoreLog "ERROR: PostgreSQL utilities (pg_restore.exe / psql.exe) were not found." "ERROR"
    exit 1
}

Write-RestoreLog "Using pg_restore: $pgRestorePath"
Write-RestoreLog "Using psql: $psqlPath"

$env:PGPASSWORD = $dbPassword

try {
    # 4. Terminate active connections
    Write-RestoreLog "Closing any active connections to '$dbName'..."
    $termSql = "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$dbName' AND pid <> pg_backend_pid();"
    & "$psqlPath" -h $dbHost -p $dbPort -U $dbUser -d postgres -c "$termSql" -q

    # 5. Clean drop and create
    Write-RestoreLog "Re-creating clean database '$dbName'..."
    $dropSql = "DROP DATABASE IF EXISTS `"$dbName`" WITH (FORCE);"
    $createSql = "CREATE DATABASE `"$dbName`" WITH ENCODING = 'UTF8';"
    & "$psqlPath" -h $dbHost -p $dbPort -U $dbUser -d postgres -c "$dropSql" -q
    & "$psqlPath" -h $dbHost -p $dbPort -U $dbUser -d postgres -c "$createSql" -q

    # 6. Restore from dump
    Write-RestoreLog "Restoring data from '$BackupFilePath'..."
    & "$pgRestorePath" -h $dbHost -p $dbPort -U $dbUser -d "$dbName" --no-owner --no-privileges "$BackupFilePath"
    $restoreExitCode = $LASTEXITCODE

    if ($restoreExitCode -le 1) {
        Write-RestoreLog "SUCCESS: Database '$dbName' restored successfully from backup!" "SUCCESS"
    } else {
        Write-RestoreLog "ERROR: pg_restore exited with code $restoreExitCode" "ERROR"
        exit 1
    }

} catch {
    Write-RestoreLog "ERROR: Restore failed: $($_.Exception.Message)" "ERROR"
    exit 1
} finally {
    $env:PGPASSWORD = ""
}

Write-RestoreLog "Database restore complete. WildBar POS is ready to launch."
Write-RestoreLog "=========================================================="
