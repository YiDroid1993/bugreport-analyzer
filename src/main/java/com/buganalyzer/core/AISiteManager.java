package com.buganalyzer.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AISiteManager {

    private static final String CONFIG_FILE = System.getProperty("user.home") + "/.buganalyzer/ai_sites.json";
    private final ObjectMapper objectMapper;
    private List<AISite> sites;

    public AISiteManager() {
        this.objectMapper = new ObjectMapper();
        this.sites = new ArrayList<>();
        loadSites();
    }

    private void loadSites() {
        File file = new File(CONFIG_FILE);
        if (file.exists()) {
            try {
                sites = objectMapper.readValue(file, new TypeReference<List<AISite>>() {});
            } catch (IOException e) {
                e.printStackTrace();
                loadDefaults();
            }
        } else {
            loadDefaults();
        }
    }

    private void loadDefaults() {
        sites.clear();
        sites.add(new AISite("ChatGPT", "https://chat.openai.com/"));
        sites.add(new AISite("Claude", "https://claude.ai/"));
        sites.add(new AISite("Gemini", "https://gemini.google.com/"));
        sites.add(new AISite("DeepSeek", "https://chat.deepseek.com/"));
        saveSites();
    }

    public void saveSites() {
        try {
            File file = new File(CONFIG_FILE);
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            objectMapper.writeValue(file, sites);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public List<AISite> getSites() {
        return sites;
    }

    public void addSite(String name, String url) {
        sites.add(new AISite(name, url));
        saveSites();
    }

    public void removeSite(AISite site) {
        sites.remove(site);
        saveSites();
    }

    public static class AISite {
        private String name;
        private String url;

        public AISite() {}

        public AISite(String name, String url) {
            this.name = name;
            this.url = url;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }

        @Override
        public String toString() {
            return name;
        }
    }
}
