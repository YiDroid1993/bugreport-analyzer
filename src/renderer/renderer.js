
document.addEventListener('DOMContentLoaded', () => {

    // 检查electronAPI是否可用
    if (!window.electronAPI) {
        console.error('electronAPI is not available');
        showError('应用程序初始化失败，请重启应用');
        return;
    }
    const newProjectBtn = document.getElementById('new-project-btn');
    const openProjectBtn = document.getElementById('open-project-btn');
    const backToListBtn = document.getElementById('back-to-list');
    const projectList = document.getElementById('project-list');
    const projectView = document.getElementById('project-view');
    const recentProjects = document.getElementById('recent-projects');
    const searchBtn = document.getElementById('search-btn');
    const searchInput = document.getElementById('search-input');
    // 添加新的事件监听
    const uploadBtn = document.getElementById('upload-btn');

    let currentProject = null;

    // 检查元素是否存在
    if (!newProjectBtn || !openProjectBtn || !backToListBtn || !projectList ||
        !projectView || !recentProjects || !searchBtn || !searchInput) {
        console.error('Required DOM elements not found');
        return;
    }

    // 加载最近项目
    loadRecentProjects();

    // 事件监听
    newProjectBtn.addEventListener('click', handleNewProject);
    openProjectBtn.addEventListener('click', handleOpenProject);
    backToListBtn.addEventListener('click', showProjectList);
    searchBtn.addEventListener('click', handleSearch);

    async function loadRecentProjects() {
        try {
            console.log('Loading recent projects...');

            if (!window.electronAPI || !window.electronAPI.listProjects) {
                console.error('electronAPI.listProjects is not available');
                displayRecentProjects([]);
                return;
            }

            const result = await window.electronAPI.listProjects();
            console.log('Recent projects result:', result);

            if (result && result.success) {
                const projects = Array.isArray(result.projects) ? result.projects : [];
                displayRecentProjects(projects);
            } else {
                console.error('Failed to load projects:', result ? result.error : 'Unknown error');
                displayRecentProjects([]);
            }
        } catch (error) {
            console.error('Error loading recent projects:', error);
            displayRecentProjects([]);
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
        try {
            console.log('Handling new project...');

            if (!window.electronAPI || !window.electronAPI.selectZipFile) {
                throw new Error('应用程序接口不可用');
            }

            const filePath = await window.electronAPI.selectZipFile();
            if (filePath) {
                const result = await window.electronAPI.processZipFile(filePath);
                if (result && result.success) {
                    openProject(result.project.id);
                    loadRecentProjects();
                } else {
                    alert('处理文件错误: ' + (result ? result.error : '未知错误'));
                }
            }
        } catch (error) {
            console.error('Error creating new project:', error);
            alert('创建项目失败: ' + error.message);
        }
    }

    async function handleOpenProject() {
        try {
            console.log('Handling open project...');

            if (!window.electronAPI || !window.electronAPI.openProjectDialog) {
                throw new Error('应用程序接口不可用');
            }

            const projectPath = await window.electronAPI.openProjectDialog();
            if (!projectPath) return;

            const projectInfo = await window.electronAPI.getProjectInfo(projectPath);
            if (!projectInfo) {
                alert('选择的目录不是有效的项目目录');
                return;
            }

            await loadProject(projectPath, projectInfo);
            await addToRecentProjects(projectPath, projectInfo);
        } catch (error) {
            console.error('Error opening project:', error);
            alert('打开项目失败: ' + error.message);
        }
    }

    // 加载项目
    async function loadProject(projectPath, projectInfo) {
        try {
            // 更新UI显示项目信息
            document.getElementById('project-name').textContent = projectInfo.name || '未命名项目';
            document.getElementById('project-path').textContent = projectPath;

            // 加载项目文件列表
            await loadProjectFiles(projectPath);

            // 显示项目视图
            showProjectView();

        } catch (error) {
            console.error('Error loading project:', error);
            throw error;
        }
    }

    // 加载项目文件
    async function loadProjectFiles(projectPath) {
        try {
            if (!projectPath) {
                console.error('Project path is undefined');
                return;
            }

            // 使用预加载脚本暴露的路径API
            const uploadsDir = await window.electronAPI.pathJoin(projectPath, 'uploads');
            const processedDir = await window.electronAPI.pathJoin(projectPath, 'processed');

            // 读取目录内容
            const uploadsResult = await window.electronAPI.readDirectory(uploadsDir);
            if (uploadsResult && uploadsResult.success) {
                displayUploadedFiles(uploadsResult.files, uploadsDir);
            } else {
                console.log('No uploads directory or error reading it');
            }

            const processedResult = await window.electronAPI.readDirectory(processedDir);
            if (processedResult && processedResult.success) {
                const bugreportFiles = processedResult.files.filter(file =>
                    file.name && file.name.toLowerCase().includes('bugreport')
                );
                displayBugreportFiles(bugreportFiles, processedDir);
            } else {
                console.log('No processed directory or error reading it');
            }
        } catch (error) {
            console.error('Error loading project files:', error);
        }
    }

    function displayBugreportFiles(files, basePath) {
        const chunkList = document.getElementById('chunk-list');
        if (!chunkList) {
            console.error('找不到用于显示BugReport文件的容器元素');
            return;
        }

        chunkList.innerHTML = ''; // 清空现有内容

        if (!files || files.length === 0) {
            chunkList.innerHTML = '<p class="no-files">未找到BugReport文件</p>';
            return;
        }

        // 创建文件列表
        const listContainer = document.createElement('div');
        listContainer.className = 'file-list-container';

        files.forEach((file, index) => {
            const fileItem = document.createElement('div');
            fileItem.className = 'file-item';

            const fileName = document.createElement('span');
            fileName.className = 'file-name';
            fileName.textContent = file.name || `bugreport_part_${index + 1}.txt`;

            const fileSize = document.createElement('span');
            fileSize.className = 'file-size';
            fileSize.textContent = formatFileSize(file.size);

            const loadButton = document.createElement('button');
            loadButton.className = 'load-button';
            loadButton.textContent = '加载';
            loadButton.addEventListener('click', () => {
                loadChunk(index); // 确保loadChunk函数也已正确定义
            });

            fileItem.appendChild(fileName);
            fileItem.appendChild(fileSize);
            fileItem.appendChild(loadButton);

            listContainer.appendChild(fileItem);
        });

        chunkList.appendChild(listContainer);
    }

    // 显示上传的文件
    function displayUploadedFiles(files, basePath) {
        const fileList = document.getElementById('uploaded-files-list');
        fileList.innerHTML = '';

        files.forEach(file => {
            const filePath = path.join(basePath, file);
            const stat = fs.statSync(filePath);

            const li = document.createElement('li');
            li.innerHTML = `
        <span class="file-name">${file}</span>
        <span class="file-size">${formatFileSize(stat.size)}</span>
        <button class="file-action" data-file="${filePath}">查看</button>
        `;

            fileList.appendChild(li);
        });
    }

    // 添加到最近项目列表
    async function addToRecentProjects(projectPath, projectInfo) {
        try {
            let recentProjects = JSON.parse(localStorage.getItem('recentProjects') || '[]');

            // 移除已存在的相同项目
            recentProjects = recentProjects.filter(p => p.path !== projectPath);

            // 添加新项目到列表开头
            recentProjects.unshift({
                name: projectInfo.name,
                path: projectPath,
                lastOpened: new Date().toISOString()
            });

            // 限制最近项目数量
            if (recentProjects.length > 10) {
                recentProjects = recentProjects.slice(0, 10);
            }

            // 保存到本地存储
            localStorage.setItem('recentProjects', JSON.stringify(recentProjects));

            // 更新UI显示
            displayRecentProjects(recentProjects);

        } catch (error) {
            console.error('Error adding to recent projects:', error);
        }
    }

    // 显示最近项目列表
    function displayRecentProjects(projects) {
        const recentProjectsList = document.getElementById('recent-projects');
        if (!recentProjectsList) {
            console.error('Recent projects list element not found');
            return;
        }

        recentProjectsList.innerHTML = '';

        // 确保projects是一个数组
        if (!Array.isArray(projects)) {
            console.error('Projects is not an array:', projects);
            recentProjectsList.innerHTML = '<li class="empty">暂无最近项目</li>';
            return;
        }

        if (projects.length === 0) {
            recentProjectsList.innerHTML = '<li class="empty">暂无最近项目</li>';
            return;
        }

        projects.forEach(project => {
            const li = document.createElement('li');
            li.innerHTML = `
            <div class="project-name">${project.name || '未命名项目'}</div>
            <div class="project-path">${project.path || '未知路径'}</div>
            <div class="project-last-opened">${project.lastOpened ? formatDate(project.lastOpened) : '未知时间'}</div>
            `;

            li.addEventListener('click', () => {
                openRecentProject(project.path);
            });

            recentProjectsList.appendChild(li);
        });
    }

    // 打开最近项目
    async function openRecentProject(projectPath) {
        try {
            const projectInfo = await window.electronAPI.getProjectInfo(projectPath);
            if (projectInfo) {
                await loadProject(projectPath, projectInfo);
            } else {
                alert('项目文件已损坏或不存在');
            }
        } catch (error) {
            console.error('Error opening recent project:', error);
            alert('打开项目失败: ' + error.message);
        }
    }

    // 文件上传处理
    async function handleFileUpload() {
        try {
            const fileInput = document.createElement('input');
            fileInput.type = 'file';
            fileInput.multiple = true;
            fileInput.accept = '.zip,.txt,.mp4';

            fileInput.addEventListener('change', async (event) => {
                const files = Array.from(event.target.files);
                if (files.length === 0) return;

                // 显示上传进度
                const uploadStatus = document.getElementById('upload-status');
                uploadStatus.textContent = `正在上传 ${files.length} 个文件...`;
                uploadStatus.className = 'status-info';

                try {
                    // 读取文件内容
                    const fileData = [];
                    for (const file of files) {
                        const buffer = await readFileAsArrayBuffer(file);
                        fileData.push({
                            name: file.name,
                            data: buffer,
                            size: file.size,
                            type: file.type
                        });
                    }

                    // 获取当前项目路径
                    const projectPath = currentProject?.path;
                    if (!projectPath) {
                        throw new Error('请先创建或打开一个项目');
                    }

                    // 上传文件到主进程
                    const result = await ipcRenderer.invoke('upload-files', projectPath, fileData);

                    if (result.success) {
                        uploadStatus.textContent = `上传成功: ${result.files.length} 个文件`;
                        uploadStatus.className = 'status-success';

                        // 刷新文件列表
                        await loadProjectFiles(projectPath);

                        // 如果是ZIP文件，自动处理
                        const zipFiles = files.filter(f => f.name.toLowerCase().endsWith('.zip'));
                        if (zipFiles.length > 0) {
                            processZipFiles(projectPath, zipFiles);
                        }
                    } else {
                        uploadStatus.textContent = `上传失败: ${result.error}`;
                        uploadStatus.className = 'status-error';
                    }
                } catch (error) {
                    console.error('Error uploading files:', error);
                    uploadStatus.textContent = `上传错误: ${error.message}`;
                    uploadStatus.className = 'status-error';
                }
            });

            fileInput.click();
        } catch (error) {
            console.error('Error handling file upload:', error);
            alert('文件上传失败: ' + error.message);
        }
    }

    // 读取文件为ArrayBuffer
    function readFileAsArrayBuffer(file) {
        return new Promise((resolve, reject) => {
            const reader = new FileReader();
            reader.onload = () => resolve(reader.result);
            reader.onerror = reject;
            reader.readAsArrayBuffer(file);
        });
    }

    // 处理ZIP文件
    async function processZipFiles(projectPath, zipFiles) {
        try {
            const processStatus = document.getElementById('process-status');
            processStatus.textContent = `正在处理 ${zipFiles.length} 个ZIP文件...`;
            processStatus.className = 'status-info';

            // 这里可以调用之前实现的ZIP处理逻辑
            for (const zipFile of zipFiles) {
                const result = await ipcRenderer.invoke('process-zip-file',
                    path.join(projectPath, 'uploads', zipFile.name));

                if (result.success) {
                    // 更新项目信息
                    const projectInfo = await ipcRenderer.invoke('get-project-info', projectPath);
                    if (!projectInfo.processedFiles) {
                        projectInfo.processedFiles = [];
                    }

                    projectInfo.processedFiles.push({
                        originalFile: zipFile.name,
                        processedAt: new Date().toISOString(),
                        result: result.project
                    });

                    await ipcRenderer.invoke('save-project-info', projectPath, projectInfo);

                    // 刷新文件列表
                    await loadProjectFiles(projectPath);
                }
            }

            processStatus.textContent = 'ZIP文件处理完成';
            processStatus.className = 'status-success';

        } catch (error) {
            console.error('Error processing ZIP files:', error);
            const processStatus = document.getElementById('process-status');
            processStatus.textContent = `处理错误: ${error.message}`;
            processStatus.className = 'status-error';
        }
    }

    // 工具函数：格式化文件大小
    function formatFileSize(bytes) {
        if (bytes === 0) return '0 B';
        const k = 1024;
        const sizes = ['B', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    }

    // 工具函数：格式化日期
    function formatDate(dateString) {
        const date = new Date(dateString);
        return date.toLocaleDateString() + ' ' + date.toLocaleTimeString();
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
    if (uploadBtn) {
        uploadBtn.addEventListener('click', handleFileUpload);
    }

    // 加载最近项目列表
    displayRecentProjects(recentProjects);
});