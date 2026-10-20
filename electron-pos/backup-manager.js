const fs = require('fs');
const path = require('path');
const os = require('os');
const { spawnSync } = require('child_process');
const { app } = require('electron');
const secureStorage = require('./secure-storage');

let lastBackupTimestamp = 0;

function getBackupConfigFile() {
  const candidatePaths = [];
  if (process.env.PORTABLE_EXECUTABLE_DIR) {
    candidatePaths.push(path.join(process.env.PORTABLE_EXECUTABLE_DIR, 'backup-config.json'));
  }
  if (app && app.isPackaged) {
    candidatePaths.push(path.join(path.dirname(process.execPath), 'backup-config.json'));
    if (process.resourcesPath) {
      candidatePaths.push(path.join(process.resourcesPath, 'backup-config.json'));
    }
  }
  candidatePaths.push(path.join(__dirname, 'backup-config.json'));
  candidatePaths.push(path.join(__dirname, '..', 'backup-config.json'));

  for (const configPath of candidatePaths) {
    if (fs.existsSync(configPath)) {
      return configPath;
    }
  }
  return null;
}

function loadBackupConfig() {
  const configPath = getBackupConfigFile();
  if (configPath) {
    try {
      const raw = fs.readFileSync(configPath, 'utf8');
      const parsed = JSON.parse(raw);
      parsed._configPath = configPath;
      return parsed;
    } catch (e) {
      console.error('[BackupManager] Error parsing', configPath, e);
    }
  }

  return {
    database: {
      host: 'localhost',
      port: 5432,
      name: 'order_management',
      username: 'postgres',
      password: 'Inventory$murali@2025'
    },
    standalone: {
      enabled: true,
      backupDirectory: 'D:\\WildBar_Backups',
      fallbackDirectory: '%USERPROFILE%\\WildBar_Backups',
      backupOnExit: true,
      backupOnLogin: true,
      backupOnLoginIntervalHours: 12,
      cloudSyncDirectory: ''
    },
    retention: {
      maxBackupsToKeep: 14,
      retentionDays: 14
    }
  };
}

/**
 * Resolves a safe, writable directory.
 * If the configured directory (e.g. D:\WildBar_Backups) fails permission check or drive doesn't exist,
 * it automatically falls back to %USERPROFILE%\WildBar_Backups, userData, or tempdir.
 */
function getWritableBackupDirectory(configuredDir, fallbackConfig) {
  const candidateDirs = [];

  // Helper to expand environment variables like %USERPROFILE%
  const expandEnv = (str) => {
    if (!str) return '';
    return str.replace(/%([^%]+)%/g, (_, n) => process.env[n] || '');
  };

  if (configuredDir) {
    const expanded = expandEnv(configuredDir);
    candidateDirs.push(path.isAbsolute(expanded) ? expanded : path.resolve(__dirname, expanded));
  }

  if (fallbackConfig) {
    candidateDirs.push(expandEnv(fallbackConfig));
  }

  // Guaranteed writable user profile path
  if (process.env.USERPROFILE) {
    candidateDirs.push(path.join(process.env.USERPROFILE, 'WildBar_Backups'));
  } else if (os.homedir()) {
    candidateDirs.push(path.join(os.homedir(), 'WildBar_Backups'));
  }

  // Electron userData
  if (app && typeof app.getPath === 'function') {
    try {
      candidateDirs.push(path.join(app.getPath('userData'), 'backups'));
    } catch (_) {}
  }

  // Absolute fallback
  candidateDirs.push(path.join(os.tmpdir(), 'WildBar_Backups'));

  for (const dir of candidateDirs) {
    if (!dir) continue;
    try {
      if (!fs.existsSync(dir)) {
        fs.mkdirSync(dir, { recursive: true });
      }
      const testFile = path.join(dir, `.wb-write-test-${Date.now()}`);
      fs.writeFileSync(testFile, 'ok');
      fs.unlinkSync(testFile);
      return dir;
    } catch (err) {
      console.warn(`[BackupManager] Directory '${dir}' is not writable (${err.message}). Trying fallback...`);
    }
  }

  return path.join(os.tmpdir(), 'WildBar_Backups');
}

function findPgDump() {
  const versions = ['17', '16', '15', '14', '13'];
  for (const v of versions) {
    const candidate = `C:\\Program Files\\PostgreSQL\\${v}\\bin\\pg_dump.exe`;
    if (fs.existsSync(candidate)) {
      return candidate;
    }
  }

  // Check PATH
  try {
    const whereResult = spawnSync('where', ['pg_dump.exe'], { encoding: 'utf8' });
    if (whereResult.status === 0 && whereResult.stdout) {
      const firstLine = whereResult.stdout.split(/\r?\n/)[0].trim();
      if (firstLine && fs.existsSync(firstLine)) return firstLine;
    }
  } catch (_) {}

  return null;
}

function applyRetention(backupDir, dbName, maxBackups, retentionDays) {
  try {
    const files = fs.readdirSync(backupDir)
      .filter(f => f.startsWith(`wildbar_${dbName}_`) && f.endsWith('.dump'))
      .map(f => {
        const fullPath = path.join(backupDir, f);
        return { name: f, fullPath, mtime: fs.statSync(fullPath).mtimeMs };
      })
      .sort((a, b) => b.mtime - a.mtime);

    const cutoffMs = Date.now() - (retentionDays * 24 * 60 * 60 * 1000);

    // Age-based cleanup
    files.forEach(f => {
      if (f.mtime < cutoffMs) {
        try { fs.unlinkSync(f.fullPath); } catch (_) {}
      }
    });

    // Count-based cleanup
    const remaining = files.filter(f => fs.existsSync(f.fullPath));
    if (remaining.length > maxBackups) {
      const excess = remaining.slice(maxBackups);
      excess.forEach(f => {
        try { fs.unlinkSync(f.fullPath); } catch (_) {}
      });
    }
  } catch (err) {
    console.warn('[BackupManager] Retention cleanup warning:', err.message);
  }
}

/**
 * Checks if a backup is due based on elapsed time since newest backup in backupDir.
 */
function isBackupDue(backupDir, dbName, intervalHours = 12) {
  try {
    if (!fs.existsSync(backupDir)) return true;
    const files = fs.readdirSync(backupDir)
      .filter(f => f.startsWith(`wildbar_${dbName}_`) && f.endsWith('.dump'))
      .map(f => {
        const fullPath = path.join(backupDir, f);
        return { name: f, mtime: fs.statSync(fullPath).mtimeMs };
      })
      .sort((a, b) => b.mtime - a.mtime);

    if (files.length === 0) return true;

    const newestTime = files[0].mtime;
    const elapsedHours = (Date.now() - newestTime) / (1000 * 60 * 60);
    return elapsedHours >= intervalHours;
  } catch (err) {
    return true;
  }
}

/**
 * Triggers database backup only if no backup was taken within intervalHours.
 * Ideal for on-login events where cashiers keep the terminal open overnight.
 */
function performBackupIfDue(reason = 'cashier-login') {
  const cfg = loadBackupConfig();
  if (!cfg.standalone || cfg.standalone.enabled === false) {
    return { success: false, message: 'Backups disabled in backup-config.json' };
  }

  const backupDir = getWritableBackupDirectory(cfg.standalone.backupDirectory, cfg.standalone.fallbackDirectory);
  const dbName = (cfg.database && cfg.database.name) || 'order_management';
  const intervalHours = (cfg.standalone && cfg.standalone.backupOnLoginIntervalHours) || 12;

  if (isBackupDue(backupDir, dbName, intervalHours)) {
    console.log(`[BackupManager] Backup is due (trigger: ${reason}, > ${intervalHours}h elapsed). Executing automated backup...`);
    return performBackup(reason);
  } else {
    console.log(`[BackupManager] Backup already exists within the last ${intervalHours}h. Skipping ${reason} backup.`);
    return { success: true, skipped: true, message: `Backup already taken within last ${intervalHours} hours` };
  }
}

/**
 * Triggers database backup immediately. Synchronous execution suitable for both on-exit and on-login hooks.
 */
function performBackup(reason = 'manual') {
  const cfg = loadBackupConfig();
  if (!cfg.standalone || cfg.standalone.enabled === false) {
    return { success: false, message: 'Backups disabled in backup-config.json' };
  }

  // Prevent multiple rapid backups within 10 seconds
  if (Date.now() - lastBackupTimestamp < 10000) {
    return { success: true, message: 'Backup skipped (already backed up moments ago)' };
  }

  const backupDir = getWritableBackupDirectory(cfg.standalone.backupDirectory, cfg.standalone.fallbackDirectory);

  const pgDump = findPgDump();
  if (!pgDump) {
    console.warn('[BackupManager] pg_dump.exe not found on system. Skipping automated backup.');
    return { success: false, error: 'pg_dump.exe not found' };
  }

  const db = cfg.database || {};
  const dbName = db.name || 'order_management';
  const dbUser = db.username || 'postgres';
  let dbPass = 'Inventory$murali@2025';
  try {
    const resolved = secureStorage.resolveAndSecurePassword(cfg, cfg._configPath);
    if (resolved) {
      dbPass = resolved;
    } else if (db.password) {
      dbPass = db.password;
    }
  } catch (e) {
    if (db.password) dbPass = db.password;
  }
  const dbHost = db.host || 'localhost';
  const dbPort = db.port || 5432;

  const now = new Date();
  const pad = (n) => String(n).padStart(2, '0');
  const dateStamp = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}_${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`;
  const backupFileName = `wildbar_${dbName}_${dateStamp}.dump`;
  const backupFilePath = path.join(backupDir, backupFileName);

  console.log(`[BackupManager] Starting database backup (${reason}): ${backupFileName}`);
  console.log(`[BackupManager] Destination directory: ${backupDir}`);

  try {
    const result = spawnSync(pgDump, [
      '-h', dbHost,
      '-p', String(dbPort),
      '-U', dbUser,
      '-F', 'c',
      '-b',
      '-f', backupFilePath,
      dbName
    ], {
      env: { ...process.env, PGPASSWORD: dbPass },
      timeout: 30000,
      stdio: ['ignore', 'ignore', 'pipe']
    });

    if (result.status === 0 && fs.existsSync(backupFilePath) && fs.statSync(backupFilePath).size > 0) {
      lastBackupTimestamp = Date.now();
      const sizeMB = (fs.statSync(backupFilePath).size / (1024 * 1024)).toFixed(2);
      const logLine = `[${new Date().toISOString()}] [SUCCESS] Created backup (${reason}, ${sizeMB} MB): ${backupFileName}\n`;
      console.log(`[BackupManager] ✓ ${logLine.trim()}`);

      try {
        fs.appendFileSync(path.join(backupDir, 'backup_history.log'), logLine, 'utf8');
      } catch (_) {}

      // Cloud / Secondary sync if configured
      if (cfg.standalone.cloudSyncDirectory && fs.existsSync(cfg.standalone.cloudSyncDirectory)) {
        try {
          fs.copyFileSync(backupFilePath, path.join(cfg.standalone.cloudSyncDirectory, backupFileName));
          console.log(`[BackupManager] ✓ Mirrored to ${cfg.standalone.cloudSyncDirectory}`);
        } catch (e) {
          console.warn('[BackupManager] Mirroring failed:', e.message);
        }
      }

      // Enforce retention limits
      const maxBackups = cfg.retention ? (cfg.retention.maxBackupsToKeep || 14) : 14;
      const retentionDays = cfg.retention ? (cfg.retention.retentionDays || 14) : 14;
      applyRetention(backupDir, dbName, maxBackups, retentionDays);

      return { success: true, file: backupFileName, sizeMB, directory: backupDir };
    } else {
      const stderr = result.stderr ? result.stderr.toString() : 'Unknown error';
      console.error('[BackupManager] pg_dump failed:', stderr);
      return { success: false, error: stderr };
    }
  } catch (err) {
    console.error('[BackupManager] Backup exception:', err.message);
    return { success: false, error: err.message };
  }
}

module.exports = {
  loadBackupConfig,
  getWritableBackupDirectory,
  isBackupDue,
  performBackupIfDue,
  performBackup
};
