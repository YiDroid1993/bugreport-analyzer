package com.buganalyzer.core;

import com.buganalyzer.model.ProjectManifest;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;

public class ProjectManager {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static void saveProject(ProjectManifest manifest, File projectDir) throws IOException {
        File jsonFile = new File(projectDir, manifest.getProjectName() + ".json");
        mapper.writerWithDefaultPrettyPrinter().writeValue(jsonFile, manifest);
    }

    public static ProjectManifest loadProject(File jsonFile) throws IOException {
        return mapper.readValue(jsonFile, ProjectManifest.class);
    }
}
