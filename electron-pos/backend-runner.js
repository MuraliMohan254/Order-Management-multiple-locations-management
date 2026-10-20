const fs = require('fs');
const path = require('path');
const os = require('os');
const { spawn, execSync } = require('child_process');
const http = require('http');
const { app } = require('electron');
const secureStorage = require('./secure-storage');

let backendProcess = null;

function getLogFilePath() {
  if (!app || !app.isPackaged) {
    return path.join(__dirname, 'local-backend.log');
  }

  const candidateDirs = [];
  if (process.env.PORTABLE_EXECUTABLE_DIR) {
    candidateDirs.push(process.env.PORTABLE_EXECUTABLE_DIR);
  }
  candidateDirs.push(path.dirname(process.execPath));
  if (app && typeof app.getPath === 'function') {
    try {
      candidateDirs.push(app.getPath('userData'));
    } catch (_) {}
  }
  candidateDirs.push(os.tmpdir());

  for (const dir of candidateDirs) {
    try {
      if (!fs.existsSync(dir)) {
        fs.mkdirSync(dir, { recursive: true });
      }
      const testFile = path.join(dir, `.wb-write-test-${Date.now()}`);
      fs.writeFileSync(testFile, 'ok');
      fs.unlinkSync(testFile);
      return path.join(dir, 'local-backend.log');
    } catch (_) {
      // Directory is not writable, try next candidate
    }
  }

  return path.join(os.tmpdir(), 'wildbar-pos-local-backend.log');
}

function loadConfig() {
  const candidatePaths = [];
  if (process.env.PORTABLE_EXECUTABLE_DIR) {
    candidatePaths.push(path.join(process.env.PORTABLE_EXECUTABLE_DIR, 'config.json'));
  }
  if (app && app.isPackaged) {
    candidatePaths.push(path.join(path.dirname(process.execPath), 'config.json'));
    if (process.resourcesPath) {
      candidatePaths.push(path.join(process.resourcesPath, 'config.json'));
    }
  }
  candidatePaths.push(path.join(__dirname, 'config.json'));

  for (const configPath of candidatePaths) {
    if (fs.existsSync(configPath)) {
      try {
        console.log(`[BackendRunner] Loading configuration from: ${configPath}`);
        return JSON.parse(fs.readFileSync(configPath, 'utf8'));
      } catch (e) {
        console.error(`[BackendRunner] Failed to parse ${configPath}:`, e);
      }
    }
  }

  return {
    mode: 'standalone',
    remoteApiUrl: 'https://api.yourwildbar.com',
    localBackend: {
      enabled: true,
      jarDirectory: './backend-jar',
      port: 8089,
      javaPath: 'java',
      jvmArgs: ['-Xmx512m']
    }
  };
}

function findJarFile(jarDir) {
  const candidateDirs = [];

  if (path.isAbsolute(jarDir)) {
    candidateDirs.push(jarDir);
  } else {
    // 1. Next to portable exe (if running portable package)
    if (process.env.PORTABLE_EXECUTABLE_DIR) {
      candidateDirs.push(path.join(process.env.PORTABLE_EXECUTABLE_DIR, jarDir));
    }
    // 2. Next to executable (e.g. release/win-unpacked/backend-jar)
    if (app && app.isPackaged) {
      candidateDirs.push(path.join(path.dirname(process.execPath), jarDir));
    }
    // 3. Electron resources folder (where extraResources places it: resources/backend-jar)
    if (process.resourcesPath) {
      candidateDirs.push(path.join(process.resourcesPath, jarDir));
    }
    // 4. Relative to current script directory (__dirname, dev mode)
    candidateDirs.push(path.join(__dirname, jarDir));
  }

  console.log('[BackendRunner] Scanning for backend JAR in:', candidateDirs);

  for (const resolvedDir of candidateDirs) {
    if (!fs.existsSync(resolvedDir)) continue;

    try {
      const files = fs.readdirSync(resolvedDir);
      const validJars = files.filter(f =>
        f.toLowerCase().endsWith('.jar') &&
        !f.toLowerCase().endsWith('-sources.jar') &&
        !f.toLowerCase().endsWith('-javadoc.jar') &&
        !f.toLowerCase().includes('.original')
      );

      if (validJars.length > 0) {
        validJars.sort((a, b) => {
          const statA = fs.statSync(path.join(resolvedDir, a));
          const statB = fs.statSync(path.join(resolvedDir, b));
          return statB.mtimeMs - statA.mtimeMs;
        });
        const selectedJar = path.join(resolvedDir, validJars[0]);
        console.log(`[BackendRunner] ✓ Found backend JAR: ${selectedJar}`);
        return selectedJar;
      }
    } catch (e) {
      console.warn(`[BackendRunner] Error scanning directory ${resolvedDir}:`, e.message);
    }
  }

  return null;
}

function findJavaExecutable(configuredPath) {
  if (configuredPath && configuredPath !== 'java') {
    if (fs.existsSync(configuredPath)) return configuredPath;
  }

  const candidatePaths = [];

  // 1. Embedded or portable JRE (jre/bin/java.exe)
  if (process.env.PORTABLE_EXECUTABLE_DIR) {
    candidatePaths.push(path.join(process.env.PORTABLE_EXECUTABLE_DIR, 'jre', 'bin', 'java.exe'));
  }
  if (app && app.isPackaged) {
    candidatePaths.push(path.join(path.dirname(process.execPath), 'jre', 'bin', 'java.exe'));
    if (process.resourcesPath) {
      candidatePaths.push(path.join(process.resourcesPath, 'jre', 'bin', 'java.exe'));
    }
  }
  candidatePaths.push(path.join(__dirname, 'jre', 'bin', 'java.exe'));

  // 2. Standard Windows Java installation folders
  const roots = [
    'C:\\Program Files\\Eclipse Adoptium',
    'C:\\Program Files\\Java',
    'C:\\Program Files\\Amazon Corretto',
    'C:\\Program Files\\Zulu',
    'C:\\Program Files\\BellSoft',
    'C:\\Program Files\\Microsoft'
  ];

  for (const root of roots) {
    if (fs.existsSync(root)) {
      try {
        const subdirs = fs.readdirSync(root);
        for (const sub of subdirs) {
          candidatePaths.push(path.join(root, sub, 'bin', 'java.exe'));
        }
      } catch (_) {}
    }
  }

  for (const p of candidatePaths) {
    if (p && fs.existsSync(p)) {
      console.log(`[BackendRunner] ✓ Discovered Java runtime at: ${p}`);
      return p;
    }
  }

  return 'java';
}

function checkPortAlive(port) {
  return new Promise((resolve) => {
    const req = http.get(`http://127.0.0.1:${port}/api/v1/public/config`, { timeout: 1500 }, (res) => {
      resolve(res.statusCode === 200 || res.statusCode === 401);
    });
    req.on('error', () => resolve(false));
    req.on('timeout', () => {
      req.destroy();
      resolve(false);
    });
  });
}

async function waitForBackend(port, maxWaitSeconds = 45, onStatusUpdate) {
  const start = Date.now();
  console.log(`[BackendRunner] Waiting for local backend on port ${port} to initialize...`);
  let lastMessage = '';
  while ((Date.now() - start) < maxWaitSeconds * 1000) {
    const isAlive = await checkPortAlive(port);
    if (isAlive) {
      console.log(`[BackendRunner] ✓ Local backend is online and responding on port ${port}!`);
      if (onStatusUpdate) onStatusUpdate('Backend connected! Loading POS interface...');
      return true;
    }
    const elapsed = Math.round((Date.now() - start) / 1000);
    let msg = `Initializing backend services (${elapsed}s)...`;
    if (elapsed < 4) {
      msg = `Booting Java runtime & PostgreSQL (${elapsed}s)...`;
    } else if (elapsed < 10) {
      msg = `Verifying database schema & migrations (${elapsed}s)...`;
    } else if (elapsed < 18) {
      msg = `Loading system settings & POS modules (${elapsed}s)...`;
    } else {
      msg = `Starting Spring Boot application (${elapsed}s)...`;
    }
    if (msg !== lastMessage && onStatusUpdate) {
      lastMessage = msg;
      onStatusUpdate(msg);
    }
    await new Promise(r => setTimeout(r, 600));
  }
  return false;
}

/**
 * Initializes and starts the local Spring Boot backend if configured in standalone mode.
 */
async function startBackendIfConfigured(onStatusUpdate) {
  const config = loadConfig();
  const isStandalone = (config.mode || 'standalone').toLowerCase() === 'standalone';

  if (!isStandalone) {
    const remoteUrl = config.remoteApiUrl ? config.remoteApiUrl.replace(/\/+$/, '') : 'https://api.yourwildbar.com';
    const apiUrl = remoteUrl.endsWith('/api/v1') ? remoteUrl : `${remoteUrl}/api/v1`;
    console.log(`[BackendRunner] Cloud Mode active. Target API: ${apiUrl}`);
    if (onStatusUpdate) onStatusUpdate('Connecting to Cloud POS API...');
    return {
      mode: 'cloud',
      apiUrl: apiUrl,
      config: config
    };
  }

  const localCfg = config.localBackend || {};
  const port = localCfg.port || 8089;
  const jarPath = findJarFile(localCfg.jarDirectory || './backend-jar');
  const apiUrl = `http://localhost:${port}/api/v1`;

  if (onStatusUpdate) onStatusUpdate('Checking local backend connection...');

  // 1. Check if backend is already running on this machine (e.g. from IDE or previous run)
  if (await checkPortAlive(port)) {
    console.log(`[BackendRunner] Backend already running on port ${port}. Connecting directly.`);
    if (onStatusUpdate) onStatusUpdate('Local backend is online! Launching POS...');
    return {
      mode: 'standalone',
      apiUrl: apiUrl,
      config: config,
      isEmbedded: false
    };
  }

  // 2. Check if JAR exists to launch
  if (!jarPath) {
    console.warn(`[BackendRunner] Standalone mode selected, but no .jar file was found in ${localCfg.jarDirectory || './backend-jar'}.`);
    console.warn(`[BackendRunner] Please drop your backend .jar into electron-pos/backend-jar/`);
    if (onStatusUpdate) onStatusUpdate('Connecting to POS Terminal...');
    return {
      mode: 'standalone',
      apiUrl: apiUrl,
      config: config,
      warning: 'No backend JAR found in backend-jar folder'
    };
  }

  console.log(`[BackendRunner] Found backend JAR: ${jarPath}`);
  if (onStatusUpdate) onStatusUpdate('Starting local database and backend server...');

  const javaCmd = findJavaExecutable(localCfg.javaPath);
  const jvmArgs = localCfg.jvmArgs || ['-Xmx512m'];
  const args = [...jvmArgs, '-jar', jarPath, `--server.port=${port}`];

  console.log(`[BackendRunner] Spawning: ${javaCmd} ${args.join(' ')}`);
  console.log(`[BackendRunner] Backend working directory: ${path.dirname(jarPath)}`);

  let logStream = null;
  const logFilePath = getLogFilePath();
  try {
    console.log(`[BackendRunner] Logging backend output to: ${logFilePath}`);
    logStream = fs.createWriteStream(logFilePath, { flags: 'a' });
    logStream.write(`\n================================================================\n`);
    logStream.write(`[${new Date().toISOString()}] Launching WildBar Backend Service\n`);
    logStream.write(`Java Executable: ${javaCmd}\n`);
    logStream.write(`Backend JAR: ${jarPath}\n`);
    logStream.write(`Target Port: ${port}\n`);
    logStream.write(`Working Directory: ${path.dirname(jarPath)}\n`);
    logStream.write(`================================================================\n`);
    logStream.on('error', (err) => {
      console.error('[BackendRunner] Log stream error:', err);
    });
  } catch (err) {
    console.error('[BackendRunner] Could not create log file stream:', err);
  }

  try {
    let dbPass = 'Inventory$murali@2025';
    try {
      const backupMgr = require('./backup-manager');
      const bCfg = backupMgr.loadBackupConfig();
      const resolved = secureStorage.resolveAndSecurePassword(bCfg, bCfg._configPath);
      if (resolved) dbPass = resolved;
    } catch (_) {}

    backendProcess = spawn(javaCmd, args, {
      cwd: path.dirname(jarPath),
      detached: false,
      env: { ...process.env, DB_PASSWORD: dbPass },
      stdio: ['ignore', 'pipe', 'pipe']
    });

    if (logStream) {
      backendProcess.stdout.pipe(logStream);
      backendProcess.stderr.pipe(logStream);
    } else {
      backendProcess.stdout.on('data', data => console.log(`[Backend stdout] ${data}`));
      backendProcess.stderr.on('data', data => console.error(`[Backend stderr] ${data}`));
    }

    backendProcess.on('error', (err) => {
      console.error('[BackendRunner] Failed to launch Java process:', err);
      if (logStream) {
        logStream.write(`[${new Date().toISOString()}] [FATAL] Failed to start Java process: ${err.message}\n`);
        if (err.code === 'ENOENT') {
          logStream.write(`[HINT] Java runtime was not found at "${javaCmd}". Please install Java 17+ (e.g. Eclipse Adoptium Temurin 17) or configure "javaPath" in config.json.\n`);
        }
      }
      if (onStatusUpdate) {
        if (err.code === 'ENOENT') {
          onStatusUpdate('Java runtime not found! Please install Java 17+ or configure javaPath.');
        } else {
          onStatusUpdate(`Backend launch error: ${err.message}`);
        }
      }
    });

    backendProcess.on('exit', (code, signal) => {
      console.log(`[BackendRunner] Java process exited with code ${code}, signal ${signal}`);
      if (logStream && code !== null && code !== 0) {
        logStream.write(`[${new Date().toISOString()}] [EXIT] Java process exited with code ${code}, signal ${signal}\n`);
      }
      backendProcess = null;
    });

    // Wait for the backend to become healthy
    const ready = await waitForBackend(port, 45, onStatusUpdate);
    if (!ready) {
      console.warn(`[BackendRunner] Warning: Backend took longer than 45s to start. Check local-backend.log`);
      if (onStatusUpdate) onStatusUpdate('Backend start taking time, launching POS...');
    } else {
      if (onStatusUpdate) onStatusUpdate('Backend online! Launching POS Terminal...');
    }

    return {
      mode: 'standalone',
      apiUrl: apiUrl,
      config: config,
      isEmbedded: true
    };
  } catch (err) {
    console.error('[BackendRunner] Error spawning backend process:', err);
    return {
      mode: 'standalone',
      apiUrl: apiUrl,
      config: config,
      error: err.message
    };
  }
}

/**
 * Cleanly terminates the local backend child process on application exit.
 */
function stopBackend() {
  if (!backendProcess) return;

  console.log(`[BackendRunner] Shutting down local backend process (PID: ${backendProcess.pid})...`);
  try {
    if (process.platform === 'win32') {
      execSync(`taskkill /pid ${backendProcess.pid} /T /F`, { stdio: 'ignore' });
    } else {
      backendProcess.kill('SIGTERM');
    }
  } catch (err) {
    console.error('[BackendRunner] Error stopping backend:', err.message);
  }
  backendProcess = null;
}

module.exports = {
  loadConfig,
  startBackendIfConfigured,
  stopBackend
};
