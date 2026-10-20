const fs = require('fs');
const path = require('path');
const { execSync, spawnSync } = require('child_process');

const JRE_DIR = path.join(__dirname, 'jre');
const JAVA_EXE = path.join(JRE_DIR, 'bin', 'java.exe');
const ADOPTIUM_URL = 'https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jre/hotspot/normal/eclipse';

function checkExistingJre() {
  if (fs.existsSync(JAVA_EXE)) {
    try {
      const res = spawnSync(JAVA_EXE, ['-version'], { encoding: 'utf8' });
      const output = (res.stderr || res.stdout || '').trim();
      const firstLine = output.split('\n')[0];
      console.log(`[EnsureJRE] ✓ Portable JRE 21 is ready at: ${JAVA_EXE}`);
      console.log(`[EnsureJRE]   ${firstLine}`);
      return true;
    } catch (err) {
      console.warn(`[EnsureJRE] Existing JRE at ${JAVA_EXE} is corrupted: ${err.message}`);
    }
  }
  return false;
}

function ensureJre() {
  if (checkExistingJre()) {
    return;
  }

  console.log('[EnsureJRE] =======================================================');
  console.log('[EnsureJRE] Bundled JRE 21 not found. Downloading official OpenJDK');
  console.log('[EnsureJRE] Temurin 21 JRE for Windows x64 (~48 MB)...');
  console.log('[EnsureJRE] Source: ' + ADOPTIUM_URL);
  console.log('[EnsureJRE] =======================================================');

  const zipPath = path.join(__dirname, 'temurin-jre-21.zip');
  const tempExtractDir = path.join(__dirname, 'temp-extract-jre');

  try {
    // 1. Download using curl (fast, reliable with redirects on Windows 10/11)
    console.log('[EnsureJRE] Downloading JRE archive...');
    execSync(`curl.exe -L "${ADOPTIUM_URL}" -o "${zipPath}"`, { stdio: 'inherit' });

    if (!fs.existsSync(zipPath) || fs.statSync(zipPath).size < 1000000) {
      throw new Error('Downloaded JRE zip file is missing or invalid.');
    }

    // 2. Extract archive
    console.log('[EnsureJRE] Extracting JRE archive...');
    if (fs.existsSync(tempExtractDir)) {
      fs.rmSync(tempExtractDir, { recursive: true, force: true });
    }
    fs.mkdirSync(tempExtractDir, { recursive: true });

    // Use built-in tar.exe if available, else PowerShell
    try {
      execSync(`tar.exe -xf "${zipPath}" -C "${tempExtractDir}"`, { stdio: 'ignore' });
    } catch (_) {
      execSync(`powershell -NoProfile -Command "Expand-Archive -LiteralPath '${zipPath}' -DestinationPath '${tempExtractDir}' -Force"`, { stdio: 'inherit' });
    }

    // 3. Find extracted root directory (e.g. jdk-21.0.12.1+1-jre)
    const subdirs = fs.readdirSync(tempExtractDir);
    const jreRoot = subdirs.find(d => {
      const p = path.join(tempExtractDir, d);
      return fs.statSync(p).isDirectory() && fs.existsSync(path.join(p, 'bin', 'java.exe'));
    });

    if (!jreRoot) {
      throw new Error('Could not find JRE bin/java.exe inside extracted archive.');
    }

    if (fs.existsSync(JRE_DIR)) {
      fs.rmSync(JRE_DIR, { recursive: true, force: true });
    }

    fs.renameSync(path.join(tempExtractDir, jreRoot), JRE_DIR);
    console.log(`[EnsureJRE] ✓ Extracted JRE to: ${JRE_DIR}`);

    // 4. Verify the newly installed JRE
    if (!checkExistingJre()) {
      throw new Error('Installed JRE failed verification.');
    }

  } finally {
    // Clean up temporary files
    try {
      if (fs.existsSync(zipPath)) fs.unlinkSync(zipPath);
      if (fs.existsSync(tempExtractDir)) fs.rmSync(tempExtractDir, { recursive: true, force: true });
    } catch (_) {}
  }
}

if (require.main === module) {
  try {
    ensureJre();
  } catch (e) {
    console.error('[EnsureJRE] FATAL ERROR:', e.message);
    process.exit(1);
  }
}

module.exports = { ensureJre, JRE_DIR, JAVA_EXE };
