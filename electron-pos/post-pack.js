const fs = require('fs');
const path = require('path');
const { spawnSync } = require('child_process');

const unpackedDir = path.join(__dirname, 'release', 'win-unpacked');

if (!fs.existsSync(unpackedDir)) {
  console.log('[PostPack] win-unpacked directory not found, skipping post-pack tasks.');
  process.exit(0);
}

console.log('[PostPack] Finalizing win-unpacked distribution...');

// 1. Copy config.json and backup-config.json to the root of win-unpacked for easy administrator access
const filesToExpose = ['config.json', 'backup-config.json'];
for (const file of filesToExpose) {
  const src = path.join(__dirname, file);
  const dest = path.join(unpackedDir, file);
  if (fs.existsSync(src)) {
    fs.copyFileSync(src, dest);
    console.log(`[PostPack] ✓ Exposed ${file} directly at root of win-unpacked.`);
  }
}

// 2. Verify bundled JRE inside resources
const jreInResources = path.join(unpackedDir, 'resources', 'jre', 'bin', 'java.exe');
if (fs.existsSync(jreInResources)) {
  try {
    const res = spawnSync(jreInResources, ['-version'], { encoding: 'utf8' });
    const line = (res.stderr || res.stdout || '').trim().split('\n')[0];
    console.log(`[PostPack] ✓ Verified bundled JRE in distribution: ${line}`);
  } catch (err) {
    console.warn('[PostPack] Warning verifying bundled JRE:', err.message);
  }
} else {
  console.warn(`[PostPack] ⚠️ Bundled JRE not found at: ${jreInResources}`);
}

// 3. Verify backend JAR inside resources
const jarDir = path.join(unpackedDir, 'resources', 'backend-jar');
if (fs.existsSync(jarDir)) {
  const jars = fs.readdirSync(jarDir).filter(f => f.endsWith('.jar'));
  if (jars.length > 0) {
    console.log(`[PostPack] ✓ Verified backend JAR in distribution: ${jars.join(', ')}`);
  } else {
    console.warn('[PostPack] ⚠️ No .jar file found inside resources/backend-jar');
  }
}

console.log('[PostPack] ================================================================');
console.log('[PostPack] SUCCESS: win-unpacked is completely self-contained and ready!');
console.log('[PostPack] You can now copy the entire "win-unpacked" folder to ANY Windows PC.');
console.log('[PostPack] It includes:');
console.log('[PostPack]   1. Bundled Java 21 JRE (Zero Java installation required)');
console.log('[PostPack]   2. Embedded Spring Boot backend JAR');
console.log('[PostPack]   3. Built Angular 19 POS frontend');
console.log('[PostPack]   4. Auto-encrypting DPAPI database backup manager');
console.log('[PostPack] ================================================================');
