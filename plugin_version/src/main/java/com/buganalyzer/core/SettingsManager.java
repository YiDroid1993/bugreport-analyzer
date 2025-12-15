package com.buganalyzer.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;

public class SettingsManager {
    private static final String CONFIG_DIR = System.getProperty("user.home") + File.separator + ".bugreport_analyzer";
    private static final String SETTINGS_FILE = "settings.json";
    private static final ObjectMapper mapper = new ObjectMapper();

    private Settings settings;

    public SettingsManager() {
        File dir = new File(CONFIG_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        loadSettings();
    }

    private void loadSettings() {
        File file = new File(CONFIG_DIR, SETTINGS_FILE);
        if (file.exists()) {
            try {
                settings = mapper.readValue(file, Settings.class);
            } catch (IOException e) {
                e.printStackTrace();
                settings = new Settings();
            }
        } else {
            settings = new Settings();
            // Set default based on OS
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                settings.setDefaultOpenDirectory(System.getProperty("user.home") + File.separator + "Desktop");
            } else {
                settings.setDefaultOpenDirectory(System.getProperty("user.home"));
            }
            saveSettings();
        }
    }

    public void saveSettings() {
        File file = new File(CONFIG_DIR, SETTINGS_FILE);
        try {
            mapper.writeValue(file, settings);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getDefaultOpenDirectory() {
        return settings.getDefaultOpenDirectory();
    }

    public void setDefaultOpenDirectory(String path) {
        settings.setDefaultOpenDirectory(path);
        saveSettings();
    }

    public static class Settings {
        private String defaultOpenDirectory;

        public String getDefaultOpenDirectory() {
            return defaultOpenDirectory;
        }

        public void setDefaultOpenDirectory(String defaultOpenDirectory) {
            this.defaultOpenDirectory = defaultOpenDirectory;
        }
    }
}
