const { contextBridge, ipcRenderer } = require('electron');

let appInfo = null;
try {
  appInfo = ipcRenderer.sendSync('get-app-info-sync');
} catch (e) {
  console.warn('[Preload] Could not fetch appInfo synchronously:', e);
}

contextBridge.exposeInMainWorld('electronAPI', {
  isElectron: true,
  appInfo: appInfo,
  apiUrl: appInfo ? appInfo.apiUrl : 'http://localhost:8089/api/v1',
  getAppInfo: () => ipcRenderer.invoke('get-app-info'),
  onStartupStatus: (callback) => {
    ipcRenderer.on('startup-status', (_event, value) => callback(value));
  },
  printTestReceipt: (ip, port, config) => ipcRenderer.invoke('print-test', { ip, port, config }),
  printKitchenOrderTicket: (ip, port, order, targetBatch, config) =>
    ipcRenderer.invoke('print-kot', { ip, port, order, targetBatch, config }),
  printCustomerBill: (ip, port, order, settledBy, config) =>
    ipcRenderer.invoke('print-bill', { ip, port, order, settledBy, config }),
  sendRawBytes: (ip, port, bytes) => ipcRenderer.invoke('print-raw', { ip, port, bytes }),
  performDatabaseBackup: () => ipcRenderer.invoke('perform-database-backup'),
  backupOnLogin: () => ipcRenderer.invoke('backup-on-login')
});
