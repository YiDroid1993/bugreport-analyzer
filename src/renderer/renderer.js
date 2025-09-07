const { ipcRenderer } = require('electron');

document.addEventListener('DOMContentLoaded', () => {
    const newProjectBtn = document.getElementById('new-project-btn');
    const openProjectBtn = document.getElementById('open-project-btn');
    const backToListBtn = document.getElementById('back-to-list');
    const projectList = document.getElementById('project-list');
    const projectView = document.getElementById('project-view');
    const recentProjects = document.getElementById('recent-projects');
    const searchBtn = document.getElementById('search-btn');
    const searchInput = document.getElementById('search-input');
    
    let currentProject = null;
    
    // 加载最近项目
    loadRecentProjects();
    
    // 事件监听
    newProjectBtn.addEventListener('click', handleNewProject);
    openProjectBtn.addEventListener('click', handleOpenProject);
    backToListBtn.addEventListener('click', showProjectList);
    searchBtn.addEventListener('click', handleSearch);
    
    async function loadRecentProjects() {
        const result = await ipcRenderer.invoke('list-projects');
        if (result.success) {
            displayProjects(result.projects);
        } else {
            console.error('Failed to load projects:', result.error);
        }
    }
    
    function displayProjects(projects) {
        recentProjects.innerHTML = '';
        
        if (projects.length === 0) {
            recentProjects.innerHTML = '<li>No recent projects</li>';
            return;
        }
        
        projects.forEach(project => {
            const li = document.createElement('li');
            li.textContent = project.name;
            li.addEventListener('click', () => openProject(project.id));
            recentProjects.appendChild(li);
        });
    }
    
    async function handleNewProject() {
        const filePath = await ipcRenderer.invoke('select-zip-file');
        if (filePath) {
            const result = await ipcRenderer.invoke('process-zip-file', filePath);
            if (result.success) {
                openProject(result.project.id);
                loadRecentProjects();
            } else {
                alert('Error processing file: ' + result.error);
            }
        }
    }
    
    async function handleOpenProject() {
        // 实现打开已有项目的逻辑
    }
    
    async function openProject(projectId) {
        const result = await ipcRenderer.invoke('load-project', projectId);
        if (result.success) {
            currentProject = result.project;
            showProjectView();
            displayProjectContent();
        } else {
            alert('Error loading project: ' + result.error);
        }
    }
    
    function showProjectView() {
        projectList.classList.add('hidden');
        projectView.classList.remove('hidden');
        document.getElementById('project-name').textContent = currentProject.name;
    }
    
    function showProjectList() {
        projectView.classList.add('hidden');
        projectList.classList.remove('hidden');
        currentProject = null;
    }
    
    function displayProjectContent() {
        displayChunkList();
        displayVideoList();
    }
    
    function displayChunkList() {
        const chunkList = document.getElementById('chunk-list');
        chunkList.innerHTML = '';
        
        if (currentProject.bugreportFiles.length === 0) {
            chunkList.innerHTML = '<p>No bugreport files found</p>';
            return;
        }
        
        currentProject.bugreportFiles[0].chunks.forEach((chunk, index) => {
            const btn = document.createElement('button');
            btn.textContent = `Part ${index + 1}`;
            btn.addEventListener('click', () => loadChunk(index));
            chunkList.appendChild(btn);
        });
    }
    
    function displayVideoList() {
        const videoList = document.getElementById('video-list');
        videoList.innerHTML = '';
        
        if (currentProject.mp4Files.length === 0) {
            videoList.innerHTML = '<p>No video files found</p>';
            return;
        }
        
        currentProject.mp4Files.forEach((videoPath, index) => {
            const btn = document.createElement('button');
            btn.textContent = `Video ${index + 1}`;
            btn.addEventListener('click', () => playVideo(videoPath));
            videoList.appendChild(btn);
        });
    }
    
    async function loadChunk(chunkIndex) {
        const result = await ipcRenderer.invoke('get-chunk-content', currentProject.id, chunkIndex);
        if (result.success) {
            document.getElementById('text-content').textContent = result.content;
        } else {
            alert('Error loading chunk: ' + result.error);
        }
    }
    
    async function handleSearch() {
        const query = searchInput.value;
        const useRegex = document.getElementById('use-regex').checked;
        
        if (!query) return;
        
        // 简单的实现 - 在实际应用中应该搜索所有块
        const result = await ipcRenderer.invoke('search-text', currentProject.id, 0, query, {
            useRegex: useRegex
        });
        
        if (result.success) {
            displaySearchResults(result.results);
        } else {
            alert('Search error: ' + result.error);
        }
    }
    
    function displaySearchResults(results) {
        const textContent = document.getElementById('text-content');
        let content = textContent.textContent;
        
        // 高亮搜索结果
        results.forEach(result => {
            const regex = new RegExp(result.content, 'g');
            content = content.replace(regex, match => `<mark>${match}</mark>`);
        });
        
        textContent.innerHTML = content;
    }
    
    function playVideo(videoPath) {
        // 在实际应用中，应该打开一个新窗口或模态框来播放视频
        alert(`Would play video: ${videoPath}`);
    }
});