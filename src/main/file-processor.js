const fs = require('fs');
const path = require('path');
const StreamZip = require('node-stream-zip');
const AdmZip = require('adm-zip');
const { v4: uuidv4 } = require('uuid');
const TextSplitter = require('./text-splitter');
const SearchEngine = require('./search-engine');

class FileProcessor {
  constructor() {
    this.projects = new Map();
    this.textSplitter = new TextSplitter();
    this.searchEngine = new SearchEngine();
  }

  async processZipFile(filePath) {
    const projectId = uuidv4();
    const projectName = path.basename(filePath, '.zip');
    const projectDir = path.join(process.cwd(), 'cache', projectId);
    
    if (!fs.existsSync(projectDir)) {
      fs.mkdirSync(projectDir, { recursive: true });
    }

    // 解压ZIP文件
    await this.extractZip(filePath, projectDir);

    // 处理嵌套ZIP文件
    await this.processNestedZips(projectDir);

    // 查找并处理bugreport文件
    const bugreportFiles = this.findBugreportFiles(projectDir);
    const processedFiles = [];

    for (const file of bugreportFiles) {
      const chunks = await this.textSplitter.splitFile(file, projectDir);
      processedFiles.push({
        originalPath: file,
        chunks: chunks
      });
    }

    // 查找MP4文件
    const mp4Files = this.findMp4Files(projectDir);

    return {
      id: projectId,
      name: projectName,
      originalPath: filePath,
      cacheDir: projectDir,
      bugreportFiles: processedFiles,
      mp4Files: mp4Files,
      createdAt: new Date().toISOString()
    };
  }

  async extractZip(zipPath, targetDir) {
    return new Promise((resolve, reject) => {
      const zip = new StreamZip.async({ file: zipPath });
      
      zip.extract(null, targetDir)
        .then(() => {
          zip.close();
          resolve();
        })
        .catch(err => {
          reject(err);
        });
    });
  }

  async processNestedZips(dir) {
    const files = fs.readdirSync(dir);
    
    for (const file of files) {
      const fullPath = path.join(dir, file);
      const stat = fs.statSync(fullPath);
      
      if (stat.isDirectory()) {
        await this.processNestedZips(fullPath);
      } else if (path.extname(file).toLowerCase() === '.zip') {
        const nestedDir = path.join(dir, path.basename(file, '.zip'));
        if (!fs.existsSync(nestedDir)) {
          fs.mkdirSync(nestedDir, { recursive: true });
        }
        
        await this.extractZip(fullPath, nestedDir);
        fs.unlinkSync(fullPath); // 删除嵌套ZIP文件
        
        // 处理新解压的文件中的嵌套ZIP
        await this.processNestedZips(nestedDir);
      }
    }
  }

  findBugreportFiles(dir) {
    const results = [];
    this._findFilesByPattern(dir, /bugreport.*\.txt$/i, results);
    return results;
  }

  findMp4Files(dir) {
    const results = [];
    this._findFilesByPattern(dir, /\.mp4$/i, results);
    return results;
  }

  _findFilesByPattern(dir, pattern, results) {
    const files = fs.readdirSync(dir);
    
    for (const file of files) {
      const fullPath = path.join(dir, file);
      const stat = fs.statSync(fullPath);
      
      if (stat.isDirectory()) {
        this._findFilesByPattern(fullPath, pattern, results);
      } else if (pattern.test(file)) {
        results.push(fullPath);
      }
    }
  }

  async searchInChunk(projectId, chunkIndex, query, options) {
    const project = this.projects.get(projectId);
    if (!project) {
      throw new Error('Project not found');
    }

    const chunkPath = project.bugreportFiles[0].chunks[chunkIndex];
    return await this.searchEngine.searchInFile(chunkPath, query, options);
  }

  async getChunkContent(projectId, chunkIndex) {
    const project = this.projects.get(projectId);
    if (!project) {
      throw new Error('Project not found');
    }

    const chunkPath = project.bugreportFiles[0].chunks[chunkIndex];
    return fs.promises.readFile(chunkPath, 'utf-8');
  }
}

module.exports = FileProcessor;