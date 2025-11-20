package com.buganalyzer.model;

import java.util.ArrayList;
import java.util.List;

public class ProjectManifest {
    private String projectName;
    private String displayName; // Alias for the project
    private String originalZipPath;
    private long createdDate;
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
