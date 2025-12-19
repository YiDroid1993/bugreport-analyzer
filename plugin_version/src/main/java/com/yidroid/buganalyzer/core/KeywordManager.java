package com.yidroid.buganalyzer.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 关键字管理器
 * <p>
 * 负责管理用户定义的关键字及其分类。
 * 实现即使久的化存储（JSON 格式），并支持从旧版 TXT 格式自动迁移。
 */
public class KeywordManager {
    /** 配置文件存储目录 (~/.bugreport_analyzer) */
    private static final String CONFIG_DIR = System.getProperty("user.home") + File.separator + ".bugreport_analyzer";
    
    /** 旧版配置文件路径（仅用于迁移） */
    private static final File KEYWORD_FILE_TXT = new File(CONFIG_DIR, "keywords.txt"); 
    /** 新版 JSON 配置文件路径 */
    private static final File KEYWORD_FILE_JSON = new File(CONFIG_DIR, "keywords.json"); 
    
    private static final ObjectMapper mapper = new ObjectMapper();

    /** 
     * 关键字分类映射表
     * Key: 分类名称
     * Value: 该分类下的关键字列表
     */
    private Map<String, List<String>> categorizedKeywords = new LinkedHashMap<>();

    public KeywordManager() {
        File dir = new File(CONFIG_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        loadKeywords();
    }

    /**
     * 加载关键字配置
     * <p>
     * 优先加载 JSON 文件。如果不存在但存在旧版 TXT 文件，则执行自动迁移。
     */
    public void loadKeywords() {
        if (KEYWORD_FILE_JSON.exists()) {
            try {
                categorizedKeywords = mapper.readValue(KEYWORD_FILE_JSON, new TypeReference<Map<String, List<String>>>() {});
            } catch (IOException e) {
                e.printStackTrace();
                categorizedKeywords = new LinkedHashMap<>();
            }
        } else if (KEYWORD_FILE_TXT.exists()) {
            // --- Logacy Migration (旧版迁移逻辑) ---
            try {
                List<String> lines = Files.readAllLines(KEYWORD_FILE_TXT.toPath()).stream()
                        .filter(line -> !line.trim().isEmpty())
                        .collect(Collectors.toList());
                
                categorizedKeywords = new LinkedHashMap<>();
                if (!lines.isEmpty()) {
                    // 旧版无分类概念，统一归入“默认”分类
                    categorizedKeywords.put("默认", lines);
                }
                saveKeywords();
                // 可选：KEYWORD_FILE_TXT.delete(); // 暂时保留以防万一
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            categorizedKeywords = new LinkedHashMap<>();
        }
    }

    /**
     * 持久化保存关键字到 JSON 文件
     */
    public void saveKeywords() {
        try {
            mapper.writeValue(KEYWORD_FILE_JSON, categorizedKeywords);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取扁平化的所有关键字列表 (用于搜索引擎)
     * 去重并合并所有分类下的关键字。
     */
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
