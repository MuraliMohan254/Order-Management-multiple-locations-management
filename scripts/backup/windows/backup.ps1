<#
.SYNOPSIS
    Production-Grade PostgreSQL Database Backup Script for Windows (Standalone POS).
.DESCRIPTION
    - Reads settings dynamically from backup-config.json
    - Automatically locates pg_dump.exe from installed PostgreSQL versions
    - Produces a compressed custom archive (.dump) with full integrity checks
    - Enforces both count-based (maxBackupsToKeep) and age-based (retentionDays) policies
    - Optionally mirrors backups to a cloud sync directory (OneDrive, Google Drive, USB)
    - Records audit history in backup_history.log
#>

param(
    [string]$ConfigPath = "",
    [string]$TriggerReason = "manual" # "manual", "exit", "startup", "scheduled"
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
        Write-Warning "Could not parse $ConfigPath, falling back to defaults: $($_.Exception.Message)"
    }
}

# Resolve Configuration Values
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
    # Auto-encrypt plaintext password and wipe from disk!
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
            Write-Host "[OK] Automatically secured PostgreSQL password using Windows DPAPI and wiped plaintext from disk." -ForegroundColor Green
        }
    } catch {}
    $dbPassword = $rawPlain
}

if (-not $dbPassword -and [Environment]::UserInteractive) {
    # Initial setup prompt: Technician enters password once
    Write-Host "==========================================================" -ForegroundColor Yellow
    Write-Host "Initial Security Setup: No database password configured." -ForegroundColor Yellow
    Write-Host "Please enter the PostgreSQL password (will be encrypted with Windows DPAPI and stored securely):" -ForegroundColor Yellow
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
                Write-Host "[OK] Password encrypted with Windows DPAPI and saved for all future runs." -ForegroundColor Green
            }
        } catch {}
        $bstr = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureInput)
        $dbPassword = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($bstr)
        [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
    }
}

$rawBackupDir = if ($config.standalone.backupDirectory) { $config.standalone.backupDirectory } else { "D:\WildBar_Backups" }
$rawFallbackDir = if ($config.standalone.fallbackDirectory) { $config.standalone.fallbackDirectory } else { "%USERPROFILE%\WildBar_Backups" }
$cloudSyncDir = if ($config.standalone.cloudSyncDirectory) { [System.Environment]::ExpandEnvironmentVariables($config.standalone.cloudSyncDirectory) } else { "" }
$maxBackups = if ($config.retention.maxBackupsToKeep) { [int]$config.retention.maxBackupsToKeep } else { 14 }
$retentionDays = if ($config.retention.retentionDays) { [int]$config.retention.retentionDays } else { 14 }

function Get-SafeBackupDirectory([string]$PreferredDir, [string]$FallbackDir) {
    $candidates = @()
    if ($PreferredDir) { $candidates += [System.Environment]::ExpandEnvironmentVariables($PreferredDir) }
    if ($FallbackDir) { $candidates += [System.Environment]::ExpandEnvironmentVariables($FallbackDir) }
    if ($env:USERPROFILE) { $candidates += (Join-Path $env:USERPROFILE "WildBar_Backups") }
    if ($env:LOCALAPPDATA) { $candidates += (Join-Path $env:LOCALAPPDATA "WildBar_Backups") }
    $candidates += (Join-Path ([System.IO.Path]::GetTempPath()) "WildBar_Backups")

    foreach ($dir in $candidates) {
        if (-not $dir) { continue }
        try {
            if (-not (Test-Path $dir)) {
                New-Item -ItemType Directory -Path $dir -Force -ErrorAction Stop | Out-Null
            }
            $testProbe = Join-Path $dir ".probe_$(Get-Date -Format 'yyyyMMdd_HHmmss')"
            Set-Content -Path $testProbe -Value "probe" -Force -ErrorAction Stop
            Remove-Item -Path $testProbe -Force -ErrorAction SilentlyContinue
            return $dir
        } catch {
            # Try next candidate
        }
    }
    return (Join-Path ([System.IO.Path]::GetTempPath()) "WildBar_Backups")
}

$backupDir = Get-SafeBackupDirectory $rawBackupDir $rawFallbackDir

function Write-BackupLog {
    param([string]$Text, [string]$Level = "INFO")
    $now = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
    $line = "[$now] [$Level] $Text"
    Write-Host $line
    if (Test-Path $backupDir) {
        $logPath = Join-Path $backupDir "backup_history.log"
        Out-File -FilePath $logPath -InputObject $line -Append -Encoding utf8
    }
}

Write-BackupLog "=========================================================="
Write-BackupLog "Starting WildBar POS Database Backup (Trigger: $TriggerReason)"
Write-BackupLog "Target Directory: $backupDir"
Write-BackupLog "Database: $dbName on $dbHost`:$dbPort (User: $dbUser)"

# 3. Locate pg_dump.exe
$pgDump = $null
$cmd = Get-Command "pg_dump.exe" -ErrorAction SilentlyContinue
if ($cmd) { $pgDump = $cmd.Source }

if (-not $pgDump) {
    $versions = @("17", "16", "15", "14", "13")
    foreach ($v in $versions) {
        $candidate = "C:\Program Files\PostgreSQL\$v\bin\pg_dump.exe"
        if (Test-Path $candidate) {
            $pgDump = $candidate
            break
        }
    }
}

if (-not $pgDump) {
    $found = Get-ChildItem -Path "C:\Program Files\PostgreSQL" -Recurse -Filter "pg_dump.exe" -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($found) { $pgDump = $found.FullName }
}

if (-not $pgDump) {
    Write-BackupLog "ERROR: pg_dump.exe was not found. Please install PostgreSQL or add to PATH." "ERROR"
    exit 1
}

Write-BackupLog "Using pg_dump: $pgDump"

# 4. Generate timestamped file name
$timestamp = (Get-Date).ToString("yyyy-MM-dd_HHmmss")
$backupFileName = "wildbar_${dbName}_${timestamp}.dump"
$backupFullPath = Join-Path $backupDir $backupFileName

# 5. Execute pg_dump
Write-BackupLog "Exporting database '$dbName' to '$backupFullPath'..."
$env:PGPASSWORD = $dbPassword

& "$pgDump" -h $dbHost -p $dbPort -U $dbUser -F c -b -f "$backupFullPath" "$dbName"
$dumpExitCode = $LASTEXITCODE

$env:PGPASSWORD = ""

if ($dumpExitCode -eq 0 -and (Test-Path $backupFullPath) -and (Get-Item $backupFullPath).Length -gt 0) {
    $sizeBytes = (Get-Item $backupFullPath).Length
    $sizeMB = [math]::Round($sizeBytes / 1MB, 2)
    Write-BackupLog "SUCCESS: Backup created ($sizeMB MB): $backupFileName" "SUCCESS"
} else {
    if (Test-Path $backupFullPath) { Remove-Item $backupFullPath -Force -ErrorAction SilentlyContinue }
    Write-BackupLog "ERROR: pg_dump failed with exit code $dumpExitCode" "ERROR"
    exit 1
}

# 6. Mirror to Secondary / Cloud folder if configured
if ($cloudSyncDir -ne "" -and (Test-Path $cloudSyncDir)) {
    try {
        $cloudDest = Join-Path $cloudSyncDir $backupFileName
        Copy-Item -Path $backupFullPath -Destination $cloudDest -Force
        Write-BackupLog "SUCCESS: Mirrored backup to $cloudDest" "SUCCESS"
    } catch {
        $errMsg = $_.Exception.Message
        Write-BackupLog "WARNING: Could not mirror backup to $cloudSyncDir - $errMsg" "WARN"
    }
}

# 7. Apply Dual Retention Policy (Count-based and Age-based)
Write-BackupLog "Applying retention policy (Keep latest $maxBackups backups, max $retentionDays days)..."
$allBackups = Get-ChildItem -Path $backupDir -Filter "wildbar_${dbName}_*.dump" | Sort-Object LastWriteTime -Descending

# A. Purge older than retentionDays
$cutoffDate = (Get-Date).AddDays(-$retentionDays)
foreach ($f in $allBackups) {
    if ($f.LastWriteTime -lt $cutoffDate) {
        Remove-Item -Path $f.FullName -Force -ErrorAction SilentlyContinue
        Write-BackupLog "Retention cleanup (Age): Removed expired backup $($f.Name)" "CLEANUP"
    }
}

# B. Enforce max count limit (keep newest N files)
$remainingBackups = Get-ChildItem -Path $backupDir -Filter "wildbar_${dbName}_*.dump" | Sort-Object LastWriteTime -Descending
if ($remainingBackups.Count -gt $maxBackups) {
    $excessFiles = $remainingBackups | Select-Object -Skip $maxBackups
    foreach ($f in $excessFiles) {
        Remove-Item -Path $f.FullName -Force -ErrorAction SilentlyContinue
        Write-BackupLog "Retention cleanup (Count): Removed excess backup $($f.Name)" "CLEANUP"
    }
}

Write-BackupLog "Backup routine completed successfully."
Write-BackupLog "=========================================================="
