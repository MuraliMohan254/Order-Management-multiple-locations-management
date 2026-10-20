const fs = require('fs');
const path = require('path');
const { spawnSync } = require('child_process');
let electronSafeStorage = null;

try {
  const electron = require('electron');
  if (electron && electron.safeStorage) {
    electronSafeStorage = electron.safeStorage;
  }
} catch (_) {}

/**
 * Decrypts a Windows DPAPI hex string using PowerShell.
 */
function decryptWithPowerShellDpapi(hex) {
  try {
    const cleanHex = hex.replace(/^enc:dpapi:/, '').trim();
    const psScript = `$s = ConvertTo-SecureString '${cleanHex}'; $b = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($s); [Console]::OutputEncoding = [System.Text.Encoding]::UTF8; Write-Host -NoNewline ([System.Runtime.InteropServices.Marshal]::PtrToStringAuto($b)); [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($b)`;
    const ps = spawnSync('powershell', ['-NoProfile', '-Command', psScript], { encoding: 'utf8', timeout: 6000 });

    if (ps.status === 0 && ps.stdout) {
      return ps.stdout.trim();
    }
  } catch (err) {
    console.warn('[SecureStorage] DPAPI decryption error:', err.message);
  }
  return null;
}

/**
 * Encrypts a plaintext string using Electron safeStorage or PowerShell DPAPI.
 */
function encryptPassword(plain) {
  if (!plain) return '';
  if (electronSafeStorage && typeof electronSafeStorage.isEncryptionAvailable === 'function' && electronSafeStorage.isEncryptionAvailable()) {
    try {
      const buffer = electronSafeStorage.encryptString(plain);
      return 'enc:safe:' + buffer.toString('base64');
    } catch (e) {
      console.warn('[SecureStorage] safeStorage encryption error, falling back to DPAPI:', e.message);
    }
  }

  // Fallback to PowerShell DPAPI
  try {
    const escaped = plain.replace(/'/g, "''");
    const psScript = `$s = ConvertTo-SecureString '${escaped}' -AsPlainText -Force; [Console]::OutputEncoding = [System.Text.Encoding]::UTF8; Write-Host -NoNewline (ConvertFrom-SecureString $s)`;
    const ps = spawnSync('powershell', ['-NoProfile', '-Command', psScript], { encoding: 'utf8', timeout: 6000 });
    if (ps.status === 0 && ps.stdout) {
      return 'enc:dpapi:' + ps.stdout.trim();
    }
  } catch (_) {}

  return plain;
}

/**
 * Decrypts an encrypted password string in memory.
 */
function decryptPassword(cipher) {
  if (!cipher) return '';

  if (cipher.startsWith('enc:safe:')) {
    const b64 = cipher.replace('enc:safe:', '');
    if (electronSafeStorage && typeof electronSafeStorage.isEncryptionAvailable === 'function' && electronSafeStorage.isEncryptionAvailable()) {
      try {
        return electronSafeStorage.decryptString(Buffer.from(b64, 'base64'));
      } catch (e) {
        console.error('[SecureStorage] Error decrypting safeStorage password:', e.message);
      }
    }
  }

  if (cipher.startsWith('enc:dpapi:') || /^[0-9a-fA-F]{100,}$/.test(cipher)) {
    const dec = decryptWithPowerShellDpapi(cipher);
    if (dec) return dec;
    return null;
  }

  return cipher;
}

/**
 * Resolves the database password.
 * If plaintext password is found in config on disk, automatically encrypts it,
 * writes the encrypted cipher to config, and WIPES the plaintext password from disk!
 */
function resolveAndSecurePassword(config, configFilePath) {
  if (!config) return 'Inventory$murali@2025';
  const db = config.database || {};

  // 1. Try decrypting existing cipher
  if (db.encryptedPassword) {
    const dec = decryptPassword(db.encryptedPassword);
    if (dec) return dec;
    console.warn('[SecureStorage] Encrypted password was created on a different machine and cannot be decrypted on this PC.');
  }

  // 2. Plaintext password found -> Automatically migrate to encrypted storage for THIS machine
  if (db.password && db.password !== 'PROMPT_ONCE') {
    const plain = db.password;
    try {
      const cipher = encryptPassword(plain);
      if (cipher && cipher !== plain && configFilePath && fs.existsSync(configFilePath)) {
        db.encryptedPassword = cipher;
        db.password = ''; // Wipe plaintext from file
        fs.writeFileSync(configFilePath, JSON.stringify(config, null, 2), 'utf8');
        console.log(`[SecureStorage] ✓ Automatically encrypted database password for this machine in ${path.basename(configFilePath)}.`);
      }
    } catch (e) {
      console.warn('[SecureStorage] Notice during password encryption migration:', e.message);
    }
    return plain;
  }

  // 3. Default fallback password if copied between systems
  return 'Inventory$murali@2025';
}

module.exports = {
  encryptPassword,
  decryptPassword,
  resolveAndSecurePassword
};
