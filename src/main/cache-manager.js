const fs = require('fs');
const path = require('path');

class CacheManager {
    constructor() {
        this.cacheDir = path.join(process.cwd(), 'cache');
        this.projectsFile = path.join(this.cacheDir, 'projects.json');
        
        // 确保缓存目录存在
        if (!fs.existsSync(this.cacheDir)) {
          fs.mkdirSync(this.cacheDir, { recursive: true });
        }
        
        // 确保项目文件存在
        if (!fs.existsSync(this.projectsFile)) {
          fs.writeFileSync(this.projectsFile, JSON.stringify([]));
        }
    }
  
    async saveProject(projectData) {
        const projects = this.listProjectsSync();
        projects.push(projectData);
        fs.writeFileSync(this.projectsFile, JSON.stringify(projects, null, 2));
        return true;
    }
    
    async loadProject(projectId) {
        const projects = this.listProjectsSync();
        return projects.find(p => p.id === projectId);
    }
    
    async listProjects() {
        return this.listProjectsSync();
    }
    
    listProjectsSync() {
        try {
            const data = fs.readFileSync(this.projectsFile, 'utf8');
            return JSON.parse(data);
        } catch (error) {
            console.error('Error reading projects file:', error);
            return [];
        }
    }
    
    async deleteProject(projectId) {
        const projects = this.listProjectsSync();
        const filteredProjects = projects.filter(p => p.id !== projectId);
        fs.writeFileSync(this.projectsFile, JSON.stringify(filteredProjects, null, 2));
      
        // 删除项目缓存目录
        const projectDir = path.join(this.cacheDir, projectId);
        if (fs.existsSync(projectDir)) {
            this.deleteFolderRecursive(projectDir);
        }
      
        return true;
    }
    
    deleteFolderRecursive(path) {
        if (fs.existsSync(path)) {
            fs.readdirSync(path).forEach((file) => {
                const curPath = path + '/' + file;
                if (fs.lstatSync(curPath).isDirectory()) {
                  this.deleteFolderRecursive(curPath);
                } else {
                  fs.unlinkSync(curPath);
                }
            });
            fs.rmdirSync(path);
        }
    }
}

module.exports = CacheManager;