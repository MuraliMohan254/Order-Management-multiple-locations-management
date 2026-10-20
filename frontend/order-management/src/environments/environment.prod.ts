export const environment = {
  production: true,
  get apiUrl() {
    const electron = typeof window !== 'undefined' ? (window as any).electronAPI : null;
    if (electron && electron.apiUrl) {
      return electron.apiUrl;
    }
    const saved = typeof localStorage !== 'undefined' ? localStorage.getItem('API_URL') : null;
    if (saved && saved.trim()) {
      return `${saved.trim().replace(/\/+$/, '')}/api/v1`;
    }
    if (typeof window !== 'undefined' && (window.location.protocol === 'file:' || !window.location.hostname)) {
      return 'http://localhost:8089/api/v1';
    }
    return '/api/v1';
  }
};
