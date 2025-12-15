package com.buganalyzer.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

public class KeywordManager {
    private static final String CONFIG_DIR = System.getProperty("user.home") + File.separator + ".bugreport_analyzer";
    private static final File KEYWORD_FILE_TXT = new File(CONFIG_DIR, "keywords.txt"); // Legacy
    private static final File KEYWORD_FILE_JSON = new File(CONFIG_DIR, "keywords.json"); // New
    private static final ObjectMapper mapper = new ObjectMapper();

    // Map<Category, List<Keywords>>
    private Map<String, List<String>> categorizedKeywords = new LinkedHashMap<>();

    public KeywordManager() {
        File dir = new File(CONFIG_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        loadKeywords();
    }

    public void loadKeywords() {
        if (KEYWORD_FILE_JSON.exists()) {
            try {
                categorizedKeywords = mapper.readValue(KEYWORD_FILE_JSON, new TypeReference<Map<String, List<String>>>() {});
            } catch (IOException e) {
                e.printStackTrace();
                categorizedKeywords = new LinkedHashMap<>();
            }
        } else if (KEYWORD_FILE_TXT.exists()) {
            // Migration from legacy txt
            try {
                List<String> lines = Files.readAllLines(KEYWORD_FILE_TXT.toPath()).stream()
                        .filter(line -> !line.trim().isEmpty())
                        .collect(Collectors.toList());
                
                categorizedKeywords = new LinkedHashMap<>();
                if (!lines.isEmpty()) {
                    categorizedKeywords.put("默认", lines);
                }
                saveKeywords();
                // Optional: KEYWORD_FILE_TXT.delete(); // Keep for safety for now
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            categorizedKeywords = new LinkedHashMap<>();
        }
    }

    public void saveKeywords() {
        try {
            mapper.writeValue(KEYWORD_FILE_JSON, categorizedKeywords);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Flattened list for Search Engine compatibility
    public List<String> getKeywords() {
        return categorizedKeywords.values().stream()
                .flatMap(List::stream)
                .distinct()
                .collect(Collectors.toList());
    }

    public Map<String, List<String>> getCategorizedKeywords() {
        return categorizedKeywords;
    }

    public void addCategory(String category) {
        if (!categorizedKeywords.containsKey(category)) {
            categorizedKeywords.put(category, new ArrayList<>());
            saveKeywords();
        }
    }

    public void removeCategory(String category) {
        categorizedKeywords.remove(category);
        saveKeywords();
    }

    public void updateCategory(String category, List<String> keywords) {
        categorizedKeywords.put(category, new ArrayList<>(keywords));
        saveKeywords();
    }
    
    public void renameCategory(String oldName, String newName) {
        if (categorizedKeywords.containsKey(oldName)) {
            List<String> keywords = categorizedKeywords.remove(oldName);
            categorizedKeywords.put(newName, keywords);
            saveKeywords();
        }
    }
}
