package com.buganalyzer.model;

import java.util.List;

public class FileMetadata {
    private String fileName;
    private long fileSize;
    private String relativePath; // Path inside the project folder
    private String originalPath; // Path inside the zip
    private FileType type;
    private List<String> splitParts; // List of filenames if split

    public enum FileType {
        BUGREPORT, VIDEO, OTHER
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
