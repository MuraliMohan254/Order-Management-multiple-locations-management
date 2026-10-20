const { app, BrowserWindow, ipcMain } = require('electron');
const path = require('path');
const fs = require('fs');
const thermalPrinter = require('./thermal-printer');
const backendRunner = require('./backend-runner');
const backupManager = require('./backup-manager');

let splashWindow = null;
let mainWindow = null;
let appInfo = {
  mode: 'standalone',
  apiUrl: 'http://localhost:8089/api/v1'
};

function getAppIcon() {
  const assetsIcon = path.join(__dirname, 'assets', 'icon.ico');
  if (fs.existsSync(assetsIcon)) return assetsIcon;
  return path.join(__dirname, 'dist', 'favicon.ico');
}

function createSplashWindow() {
  splashWindow = new BrowserWindow({
    width: 520,
    height: 350,
    frame: false,
    resizable: false,
    center: true,
    show: true,
    alwaysOnTop: true,
    transparent: true,
    backgroundColor: '#00000000',
    icon: getAppIcon(),
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      nodeIntegration: false,
      contextIsolation: true
    }
  });

  splashWindow.loadFile(path.join(__dirname, 'splash.html'));

  splashWindow.on('closed', () => {
    splashWindow = null;
  });
}

function resolveAndPatchIndexHtml() {
  let indexPath = path.join(__dirname, 'dist', 'index.html');
  if (!fs.existsSync(indexPath)) {
    const nested = path.join(__dirname, 'dist', 'browser', 'index.html');
    if (fs.existsSync(nested)) {
      indexPath = nested;
    }
  }

  if (fs.existsSync(indexPath)) {
    try {
      if (!app.isPackaged) {
        let html = fs.readFileSync(indexPath, 'utf8');
        if (html.includes('<base href="/">')) {
          html = html.replace('<base href="/">', '<base href="./">');
          fs.writeFileSync(indexPath, html, 'utf8');
          console.log('[Electron] Automatically patched <base href="./"> for manual copy');
        }
      }
    } catch (e) {
      console.warn('[Electron] Could not check/patch base href:', e);
    }
    return indexPath;
  }
  return null;
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1366,
    height: 868,
    minWidth: 1024,
    minHeight: 700,
    title: 'WildBar POS Terminal',
    show: false, // Initially hidden while loading
    icon: getAppIcon(),
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      nodeIntegration: false,
      contextIsolation: true,
      webSecurity: false // Allows loading local file assets cleanly with Angular
    }
  });

  // Enable F11 fullscreen for POS kiosk display
  mainWindow.setMenuBarVisibility(false);

  const isDev = process.argv.includes('--dev');
  if (isDev) {
    console.log('Running in DEV mode, loading http://localhost:4200');
    mainWindow.loadURL('http://localhost:4200');
    mainWindow.webContents.openDevTools();
  } else {
    const indexPath = resolveAndPatchIndexHtml();
    if (indexPath) {
      console.log(`Loading production UI from: ${indexPath}`);
      mainWindow.loadFile(indexPath);
    } else {
      console.error('Error: index.html not found in dist/. Please copy frontend dist files into electron-pos/dist/');
      mainWindow.loadURL('data:text/html,<h2>dist/index.html not found</h2><p>Please copy your built frontend files into the <b>electron-pos/dist/</b> folder.</p>');
    }
  }

  mainWindow.once('ready-to-show', () => {
    mainWindow.show();
    if (splashWindow && !splashWindow.isDestroyed()) {
      splashWindow.close();
      splashWindow = null;
    }
  });

  mainWindow.on('closed', () => {
    mainWindow = null;
  });
}

// IPC Handlers
ipcMain.on('get-app-info-sync', (event) => {
  event.returnValue = appInfo;
});

ipcMain.handle('get-app-info', () => {
  return appInfo;
});

// Thermal Printing IPC Handlers
ipcMain.handle('print-test', async (event, { ip, port, config }) => {
  console.log(`[Electron IPC] print-test called for ${ip}:${port}`);
  return await thermalPrinter.printTestReceipt(ip, port, config);
});

ipcMain.handle('print-kot', async (event, { ip, port, order, targetBatch, config }) => {
  console.log(`[Electron IPC] print-kot called for order #${order ? order.orderNumber : '?'}, batch: ${targetBatch}`);
  return await thermalPrinter.printKitchenOrderTicket(ip, port, order, targetBatch, config);
});

ipcMain.handle('print-bill', async (event, { ip, port, order, settledBy, config }) => {
  console.log(`[Electron IPC] print-bill called for order #${order ? order.orderNumber : '?'}`);
  return await thermalPrinter.printCustomerBill(ip, port, order, settledBy, config);
});

ipcMain.handle('print-raw', async (event, { ip, port, bytes }) => {
  console.log(`[Electron IPC] print-raw called with ${bytes ? bytes.length : 0} bytes`);
  return await thermalPrinter.sendRawBytes(ip, port, Buffer.from(bytes));
});

ipcMain.handle('perform-database-backup', async () => {
  console.log('[Electron IPC] Manual database backup requested via IPC');
  return backupManager.performBackup('manual-ipc');
});

ipcMain.handle('backup-on-login', async () => {
  return backupManager.performBackupIfDue('cashier-login-ipc');
});

// App Lifecycle
app.whenReady().then(async () => {
  const { session } = require('electron');
  // Monitor cashier login completion automatically
  session.defaultSession.webRequest.onCompleted({ urls: ['*://*/api/v1/login*'] }, (details) => {
    if (details.statusCode === 200) {
      console.log('[Electron] Cashier successfully authenticated (JWT issued). Checking database backup schedule...');
      setTimeout(() => {
        backupManager.performBackupIfDue('cashier-login');
      }, 1000);
    }
  });

  createSplashWindow();

  const updateSplash = (status) => {
    console.log(`[Startup Status] ${status}`);
    if (splashWindow && !splashWindow.isDestroyed()) {
      splashWindow.webContents.send('startup-status', status);
    }
  };

  updateSplash('Checking backend services...');

  try {
    appInfo = await backendRunner.startBackendIfConfigured(updateSplash);
  } catch (err) {
    console.error('Error during backend initialization:', err);
    updateSplash('Starting POS interface...');
  }

  updateSplash('Loading POS Terminal...');
  await new Promise(r => setTimeout(r, 400));

  createWindow();

  // Fallback safety timeout
  setTimeout(() => {
    if (mainWindow && !mainWindow.isVisible()) {
      mainWindow.show();
    }
    if (splashWindow && !splashWindow.isDestroyed()) {
      splashWindow.close();
      splashWindow = null;
    }
  }, 8000);

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createWindow();
    }
  });
});

app.on('before-quit', () => {
  try {
    const cfg = backupManager.loadBackupConfig();
    if (cfg.standalone && cfg.standalone.backupOnExit) {
      console.log('[Electron] Running automatic database backup before exit...');
      backupManager.performBackup('app-exit');
    }
  } catch (e) {
    console.error('[Electron] Error running exit backup:', e.message);
  }
  backendRunner.stopBackend();
});

app.on('window-all-closed', () => {
  backendRunner.stopBackend();
  if (process.platform !== 'darwin') {
    app.quit();
  }
});
