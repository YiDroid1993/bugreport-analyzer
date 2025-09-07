const { app, BrowserWindow, ipcMain, dialog } = require('electron');
const path = require('path');
const fs = require('fs');
const FileProcessor = require('./src/main/file-processor');
const CacheManager = require('./src/main/cache-manager');

let mainWindow;
let fileProcessor;
let cacheManager;

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1200,
    height: 800,
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true,
      preload: path.join(__dirname, 'src', 'renderer', 'preload.js')
    },
    icon: path.join(__dirname, 'assets', 'icons', 'icon.png')
  });

  mainWindow.loadFile('index.html');

  // 初始化模块
  fileProcessor = new FileProcessor();
  cacheManager = new CacheManager();
}

app.whenReady().then(() => {
  createWindow();

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});

// IPC处理程序
ipcMain.handle('select-zip-file', async () => {
  const result = await dialog.showOpenDialog(mainWindow, {
    properties: ['openFile'],
    filters: [
      { name: 'Zip Files', extensions: ['zip'] }
    ]
  });

  if (!result.canceled) {
    return result.filePaths[0];
  }
  return null;
});

ipcMain.handle('process-zip-file', async (event, filePath) => {
  try {
    const projectData = await fileProcessor.processZipFile(filePath);
    await cacheManager.saveProject(projectData);
    return { success: true, project: projectData };
  } catch (error) {
    return { success: false, error: error.message };
  }
});

ipcMain.handle('load-project', async (event, projectId) => {
  try {
    const project = await cacheManager.loadProject(projectId);
    return { success: true, project };
  } catch (error) {
    return { success: false, error: error.message };
  }
});

ipcMain.handle('list-projects', async () => {
  try {
    const projects = await cacheManager.listProjects();
    return { success: true, projects };
  } catch (error) {
    return { success: false, error: error.message };
  }
});

ipcMain.handle('search-text', async (event, projectId, chunkIndex, query, options) => {
  try {
    const results = await fileProcessor.searchInChunk(projectId, chunkIndex, query, options);
    return { success: true, results };
  } catch (error) {
    return { success: false, error: error.message };
  }
});

ipcMain.handle('get-chunk-content', async (event, projectId, chunkIndex) => {
  try {
    const content = await fileProcessor.getChunkContent(projectId, chunkIndex);
    return { success: true, content };
  } catch (error) {
    return { success: false, error: error.message };
  }
});