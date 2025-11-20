package com.buganalyzer.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class RecentProjectsManager {
    private static final String CONFIG_DIR = System.getProperty("user.home") + File.separator + ".bugreport_analyzer";
    private static final String PROJECTS_FILE = "project_list.json";
    private static final ObjectMapper mapper = new ObjectMapper();

    private List<RecentProject> recentProjects;

    public RecentProjectsManager() {
        File dir = new File(CONFIG_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        loadProjects();
    }

    private void loadProjects() {
        File file = new File(CONFIG_DIR, PROJECTS_FILE);
        if (file.exists()) {
            try {
                recentProjects = mapper.readValue(file, new TypeReference<List<RecentProject>>() {});
            } catch (IOException e) {
                e.printStackTrace();
                recentProjects = new ArrayList<>();
            }
        } else {
            recentProjects = new ArrayList<>();
        }
    }

    public void addProject(String name, String path) {
        // Remove existing entry if present to move it to top
        recentProjects.removeIf(p -> p.getPath().equals(path));
        recentProjects.add(0, new RecentProject(name, path, System.currentTimeMillis()));
        
        // Limit to 10 recent projects
        if (recentProjects.size() > 10) {
            recentProjects = recentProjects.subList(0, 10);
        }
        saveProjects();
    }

    public void removeProject(String path) {
        recentProjects.removeIf(p -> p.getPath().equals(path));
        saveProjects();
    }

    public void renameProject(String path, String newName) {
        for (RecentProject p : recentProjects) {
            if (p.getPath().equals(path)) {
                p.setName(newName);
                break;
            }
        }
        saveProjects();
    }

    public void validateProjects() {
        boolean changed = recentProjects.removeIf(p -> {
            File file = new File(p.getPath());
            // Check if the file exists or if it's a directory (for project roots)
            return !file.exists();
        });
        
        if (changed) {
            saveProjects();
        }
    }

    private void saveProjects() {
        File file = new File(CONFIG_DIR, PROJECTS_FILE);
        try {
            mapper.writeValue(file, recentProjects);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public List<RecentProject> getRecentProjects() {
        return recentProjects;
    }

    public static class RecentProject {
        private String name;
        private String path;
        private long timestamp;

        public RecentProject() {}

        public RecentProject(String name, String path, long timestamp) {
            this.name = name;
            this.path = path;
            this.timestamp = timestamp;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
        
        @Override
        public String toString() {
            return name + " (" + path + ")";
        }
    }
}
