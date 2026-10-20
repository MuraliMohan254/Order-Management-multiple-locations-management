const fs = require('fs');
const path = require('path');

const srcDir = path.resolve(__dirname, '../frontend/order-management/dist/order-management/browser');
const destDir = path.resolve(__dirname, 'dist');

function copyRecursiveSync(src, dest) {
  const exists = fs.existsSync(src);
  const stats = exists && fs.statSync(src);
  const isDirectory = exists && stats.isDirectory();

  if (isDirectory) {
    if (!fs.existsSync(dest)) {
      fs.mkdirSync(dest, { recursive: true });
    }
    fs.readdirSync(src).forEach((childItemName) => {
      copyRecursiveSync(path.join(src, childItemName), path.join(dest, childItemName));
    });
  } else {
    fs.copyFileSync(src, dest);
  }
}

console.log('Checking frontend build artifacts...');
if (!fs.existsSync(srcDir)) {
  console.error(`Error: Source directory ${srcDir} does not exist. Please run "npm run build" in frontend/order-management first.`);
  process.exit(1);
}

console.log(`Copying built files from: ${srcDir}`);
console.log(`To destination: ${destDir}`);

if (fs.existsSync(destDir)) {
  fs.rmSync(destDir, { recursive: true, force: true });
}

copyRecursiveSync(srcDir, destDir);

// Update base href in index.html to ./ for Electron file compatibility
const indexPath = path.join(destDir, 'index.html');
if (fs.existsSync(indexPath)) {
  let html = fs.readFileSync(indexPath, 'utf8');
  html = html.replace('<base href="/">', '<base href="./">');
  fs.writeFileSync(indexPath, html, 'utf8');
  console.log('Updated <base href="./"> in dist/index.html');
}

// Ensure billing icon is copied into dist
const assetsIco = path.join(__dirname, 'assets', 'icon.ico');
const assetsPng = path.join(__dirname, 'assets', 'icon.png');
if (fs.existsSync(assetsIco)) {
  fs.copyFileSync(assetsIco, path.join(destDir, 'favicon.ico'));
  console.log('Synchronized assets/icon.ico -> dist/favicon.ico');
}
if (fs.existsSync(assetsPng)) {
  fs.copyFileSync(assetsPng, path.join(destDir, 'icon.png'));
  console.log('Synchronized assets/icon.png -> dist/icon.png');
}

// Synchronize root backup-config.json if updated
const rootBackupConfig = path.resolve(__dirname, '../backup-config.json');
const localBackupConfig = path.join(__dirname, 'backup-config.json');
if (fs.existsSync(rootBackupConfig)) {
  fs.copyFileSync(rootBackupConfig, localBackupConfig);
  console.log('Synchronized root backup-config.json -> electron-pos/backup-config.json');
}

console.log('Dist successfully copied and prepared for Electron!');
