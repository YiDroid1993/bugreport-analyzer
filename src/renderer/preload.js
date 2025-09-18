const { contextBridge, ipcRenderer } = require('electron');

console.log('Preload script loaded successfully');

// 向渲染进程暴露安全的API
contextBridge.exposeInMainWorld('electronAPI', {
    // 文件操作API
    selectZipFile: () => {
        console.log('selectZipFile called from renderer');
        return ipcRenderer.invoke('select-zip-file');
    },
    processZipFile: (filePath) => {
        console.log('processZipFile called from renderer');
        return ipcRenderer.invoke('process-zip-file', filePath);
    },
    loadProject: (projectId) => {
        console.log('loadProject called from renderer');
        return ipcRenderer.invoke('load-project', projectId);
    },
    listProjects: () => {
        console.log('listProjects called from renderer');
        return ipcRenderer.invoke('list-projects');
    },
    searchText: (projectId, chunkIndex, query, options) => {
        console.log('searchText called from renderer');
        return ipcRenderer.invoke('search-text', projectId, chunkIndex, query, options);
    },
    getChunkContent: (projectId, chunkIndex) => {
        console.log('getChunkContent called from renderer');
        return ipcRenderer.invoke('get-chunk-content', projectId, chunkIndex);
    },
    
    // 项目操作API
    openProjectDialog: () => {
        console.log('openProjectDialog called from renderer');
        return ipcRenderer.invoke('open-project-dialog');
    },
    getProjectInfo: (projectPath) => {
        console.log('getProjectInfo called from renderer');
        return ipcRenderer.invoke('get-project-info', projectPath);
    },
    saveProjectInfo: (projectPath, projectInfo) => {
        console.log('saveProjectInfo called from renderer');
        return ipcRenderer.invoke('save-project-info', projectPath, projectInfo);
    },
    uploadFiles: (projectPath, files) => {
        console.log('uploadFiles called from renderer');
        return ipcRenderer.invoke('upload-files', projectPath, files);
    },
    
    // 路径操作API
    pathJoin: (...args) => {
        console.log('pathJoin called from renderer');
        return ipcRenderer.invoke('path-join', ...args);
    },
    pathBasename: (filePath, ext) => {
        console.log('pathBasename called from renderer');
        return ipcRenderer.invoke('path-basename', filePath, ext);
    },
    pathDirname: (filePath) => {
        console.log('pathDirname called from renderer');
        return ipcRenderer.invoke('path-dirname', filePath);
    },
    pathExtname: (filePath) => {
        console.log('pathExtname called from renderer');
        return ipcRenderer.invoke('path-extname', filePath);
    },
    
    // 目录操作API
    readDirectory: (dirPath) => {
        console.log('readDirectory called from renderer');
        return ipcRenderer.invoke('read-directory', dirPath);
    },
    
    // 其他必要的API
    platform: process.platform,
    versions: process.versions
});

console.log('electronAPI exposed to renderer');