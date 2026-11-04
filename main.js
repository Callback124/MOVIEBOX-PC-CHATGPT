const { app, BrowserWindow, shell } = require('electron');
const http = require('http');
const fs = require('fs');
const path = require('path');

// The UI is served from a tiny local server (fixed port so localStorage persists,
// and so YouTube embeds get a proper http origin/referrer).
const PORT = 38417;
const MIME = {
  '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8', '.svg': 'image/svg+xml', '.png': 'image/png', '.ico': 'image/x-icon'
};

function startServer() {
  const root = path.join(__dirname, 'renderer');
  const server = http.createServer((req, res) => {
    let p = decodeURIComponent(new URL(req.url, 'http://x').pathname);
    if (p === '/') p = '/index.html';
    const file = path.normalize(path.join(root, p));
    if (!file.startsWith(root)) { res.writeHead(403).end(); return; }
    fs.readFile(file, (err, data) => {
      if (err) { res.writeHead(404).end(); return; }
      res.writeHead(200, { 'Content-Type': MIME[path.extname(file)] || 'application/octet-stream' });
      res.end(data);
    });
  });
  return new Promise((resolve) => {
    server.once('error', () => server.listen(0, '127.0.0.1', () => resolve(server.address().port)));
    server.listen(PORT, '127.0.0.1', () => resolve(PORT));
  });
}

async function createWindow() {
  const port = await startServer();
  const win = new BrowserWindow({
    width: 1280, height: 820, minWidth: 900, minHeight: 600,
    backgroundColor: '#0a0a10', autoHideMenuBar: true, title: 'MovieBox',
    webPreferences: { contextIsolation: true, nodeIntegration: false, sandbox: true }
  });
  const origin = `http://127.0.0.1:${port}`;
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (/^https:\/\//.test(url)) shell.openExternal(url);
    return { action: 'deny' };
  });
  win.webContents.on('will-navigate', (e, url) => {
    if (!url.startsWith(origin)) { e.preventDefault(); if (/^https:\/\//.test(url)) shell.openExternal(url); }
  });
  win.loadURL(origin + '/');
}

app.whenReady().then(createWindow);
app.on('window-all-closed', () => app.quit());
