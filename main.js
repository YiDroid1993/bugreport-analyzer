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
            nodeIntegration: false,        // 禁用Node.js集成
            contextIsolation: true,        // 启用上下文隔离
            enableRemoteModule: false,     // 禁用远程模块
            preload: path.join(__dirname, 'src', 'renderer', 'preload.js') // 预加载脚本路径
        },
        icon: path.join(__dirname, 'assets', 'icons', 'icon.png')
    });

    mainWindow.loadFile('index.html');
    // 开发模式下打开开发者工具
    if (process.env.NODE_ENV === 'development') {
        mainWindow.webContents.openDevTools();
    }

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
// 在IPC处理程序部分添加以下代码
ipcMain.handle('path-join', async (event, ...args) => {
    try {
      // 确保所有参数都是字符串
      const stringArgs = args.map(arg => {
        if (arg === undefined || arg === null) {
          return '';
        }
        return String(arg);
      });
      
      return path.join(...stringArgs);
    } catch (error) {
      console.error('Error in path-join:', error);
      return '';
    }
});

ipcMain.handle('path-basename', async (event, filePath, ext) => {
    try {
      if (!filePath) return '';
      return path.basename(String(filePath), ext);
    } catch (error) {
      console.error('Error in path-basename:', error);
      return '';
    }
});

ipcMain.handle('path-dirname', async (event, filePath) => {
    try {
       if (!filePath) return '';
        return path.dirname(String(filePath));
    } catch (error) {
        console.error('Error in path-dirname:', error);
        return '';
    }
});

ipcMain.handle('path-extname', async (event, filePath) => {
    try {
       if (!filePath) return '';
       return path.extname(String(filePath));
    } catch (error) {
       console.error('Error in path-extname:', error);
       return '';
    }
});
// 在main.js中添加目录读取API
ipcMain.handle('read-directory', async (event, dirPath) => {
  try {
    if (!fs.existsSync(dirPath)) {
      return { success: true, files: [] };
    }

    const files = fs.readdirSync(dirPath);
    const fileStats = files.map(file => {
      const filePath = path.join(dirPath, file);
      const stat = fs.statSync(filePath);
      return {
        name: file,
        path: filePath,
        isDirectory: stat.isDirectory(),
        size: stat.size,
        modified: stat.mtime
      };
    });

    return { success: true, files: fileStats };
  } catch (error) {
    console.error('Error reading directory:', error);
    return { success: false, error: error.message };
  }
});
// 打开项目对话框
ipcMain.handle('open-project-dialog', async () => {
  const result = await dialog.showOpenDialog(mainWindow, {
    properties: ['openDirectory'],
    title: '选择项目目录'
  });

  if (!result.canceled) {
    return result.filePaths[0];
  }
  return null;
});

// 获取项目信息
ipcMain.handle('get-project-info', async (event, projectPath) => {
    try {
        // 检查 projectPath 是否有效
        if (!projectPath || typeof projectPath !== 'string') {
            console.error('Invalid project path:', projectPath);
            return { success: false, error: 'Invalid project path', project: null };
        }
      
        const projectInfoPath = path.join(projectPath, 'project.json');
      
        // 检查文件是否存在
        if (!fs.existsSync(projectInfoPath)) {
            console.error('Project info file does not exist:', projectInfoPath);
            return { success: false, error: 'Project info file not found', project: null };
        }
      
        // 读取并解析项目信息
        const data = fs.readFileSync(projectInfoPath, 'utf8');
        const projectInfo = JSON.parse(data);
      
        return { success: true, project: projectInfo };
    } catch (error) {
        console.error('Error reading project info:', error);
        return { success: false, error: error.message, project: null };
    }
});

// 保存项目信息
ipcMain.handle('save-project-info', async (event, projectPath, projectInfo) => {
  try {
    const projectInfoPath = path.join(projectPath, 'project.json');
    fs.writeFileSync(projectInfoPath, JSON.stringify(projectInfo, null, 2));
    return true;
  } catch (error) {
    console.error('Error saving project info:', error);
    return false;
  }
});

// 上传文件处理
ipcMain.handle('upload-files', async (event, projectPath, files) => {
  try {
    const uploadDir = path.join(projectPath, 'uploads');
    if (!fs.existsSync(uploadDir)) {
      fs.mkdirSync(uploadDir, { recursive: true });
    }

    const results = [];
    for (const file of files) {
      const filePath = path.join(uploadDir, file.name);
      fs.writeFileSync(filePath, Buffer.from(file.data));
      results.push({
        name: file.name,
        path: filePath,
        size: file.size,
        uploadedAt: new Date().toISOString()
      });
    }

    return { success: true, files: results };
  } catch (error) {
    console.error('Error uploading files:', error);
    return { success: false, error: error.message };
  }
});


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
    return { success: true, projects: projects || [] };
  } catch (error) {
    console.error('Error listing projects:', error);
    return { success: false, error: error.message, projects: [] };
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