package com.yidroid.buganalyzer.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 项目清单类 (Manifest)
 * <p>
 * 定义“分析项目”的元数据结构。
 * 此类对象会被序列化为 JSON 存储在每个项目的根目录下，用于恢复项目状态。
 */
public class ProjectManifest {
    /** 项目名称（通常是导入时的 Zip 文件名或用户指定名） */
    private String projectName;
    
    /** 显示名称（项目的别名，UI 显示用） */
    private String displayName; 
    
    /** 原始 Zip 包的绝对路径（用于溯源或重新导入） */
    private String originalZipPath;
    
    /** 创建时间戳 */
    private long createdDate;
    
    /** 包含的文件列表 */
    private List<FileMetadata> files = new ArrayList<>();

    public ProjectManifest() {}

    public ProjectManifest(String projectName, String originalZipPath) {
        this.projectName = projectName;
        this.originalZipPath = originalZipPath;
        this.createdDate = System.currentTimeMillis();
    }

    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getOriginalZipPath() { return originalZipPath; }
    public void setOriginalZipPath(String originalZipPath) { this.originalZipPath = originalZipPath; }

    public long getCreatedDate() { return createdDate; }
    public void setCreatedDate(long createdDate) { this.createdDate = createdDate; }

    public List<FileMetadata> getFiles() { return files; }
    public void setFiles(List<FileMetadata> files) { this.files = files; }
    
    public void addFile(FileMetadata file) {
        this.files.add(file);
    }
}
