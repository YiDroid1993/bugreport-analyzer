package com.yidroid.buganalyzer.model;

import java.util.List;

/**
 * 文件元数据类
 * <p>
 * 描述分析项目中单个文件的属性。
 * 尤其处理了大型文件被拆分的逻辑（Split Parts）。
 */
public class FileMetadata {
    /** 文件名（通常不仅是显示名，也是实际存储的文件名） */
    private String fileName;
    
    /** 文件大小（字节） */
    private long fileSize;
    
    /** 项目内的相对路径 */
    private String relativePath; 
    
    /** 原始压缩包中的路径（用于溯源） */
    private String originalPath; 
    
    /** 文件类型枚举 */
    private FileType type;
    
    /** 
     * 拆分文件列表
     * <p>
     * 如果文件过大被拆分，此列表存储按顺序排列的子文件名称。
     * UI 层读取时会将这些部分视为一个逻辑整体。
     */
    private List<String> splitParts; 

    /** 文件类型定义 */
    public enum FileType {
        /** Bug Report 文本日志 */
        BUGREPORT, 
        /** 视频录屏文件 */
        VIDEO, 
        /** 其他类型文件 */
        OTHER
    }

    public FileMetadata() {}

    public FileMetadata(String fileName, long fileSize, String relativePath, String originalPath, FileType type) {
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.relativePath = relativePath;
        this.originalPath = originalPath;
        this.type = type;
    }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public String getRelativePath() { return relativePath; }
    public void setRelativePath(String relativePath) { this.relativePath = relativePath; }

    public String getOriginalPath() { return originalPath; }
    public void setOriginalPath(String originalPath) { this.originalPath = originalPath; }

    public FileType getType() { return type; }
    public void setType(FileType type) { this.type = type; }

    public List<String> getSplitParts() { return splitParts; }
    public void setSplitParts(List<String> splitParts) { this.splitParts = splitParts; }
}
