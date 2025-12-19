package com.yidroid.buganalyzer.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 核心搜索引擎类
 * <p>
 * 提供高性能、低内存占用的文件搜索服务。
 * 针对 GB 级大文件进行优化，使用流式读取（BufferedReader）避免一次性加载文件到内存。
 * 支持正则表达式、忽略大小写以及复杂的关键字组合过滤（AND/OR 逻辑）。
 */
public class SearchEngine {

    /**
     * 搜索结果数据模型
     * 保存单行匹配结果的元数据。
     */
    public static class SearchResult {
        /** 匹配文件路径（通常是文件名或相对路径） */
        public String filePath;
        /** 匹配行的行号（从 1 开始） */
        public int lineNumber;
        /** 匹配行的原始内容 */
        public String lineContent;

        public SearchResult(String filePath, int lineNumber, String lineContent) {
            this.filePath = filePath;
            this.lineNumber = lineNumber;
            this.lineContent = lineContent;
        }
    }

    /**
     * 执行文件搜索的核心方法
     *
     * @param file             目标搜索文件
     * @param query            搜索查询字符串（文本或正则表达式）
     * @param isRegex          是否将 query 视为正则表达式
     * @param ignoreCase       是否忽略大小写
     * @param keywords         关键字过滤器列表（可选）
     * @param matchAllKeywords 关键字过滤逻辑开关：
     *                         true = AND 逻辑（必须包含所有关键字）；
     *                         false = OR 逻辑（包含任意一个关键字即可）。
     * @return 匹配结果列表 {@link SearchResult}
     * @throws IOException 如果文件读取失败
     * @throws IllegalArgumentException 如果正则表达式无效
     */
    public static List<SearchResult> searchFile(File file, String query, boolean isRegex, boolean ignoreCase, List<String> keywords, boolean matchAllKeywords) throws IOException {
        List<SearchResult> results = new ArrayList<>();
        Pattern pattern = null;
        String lowerQuery = null;

        // --- 1. 参数校验与预处理 ---
        boolean hasQuery = query != null && !query.isEmpty();
        boolean hasKeywords = keywords != null && !keywords.isEmpty();
        
        // 如果既没有查询词也没有关键字，直接返回空结果
        if (!hasQuery && !hasKeywords) return results;

        if (hasQuery) {
            if (isRegex) {
                try {
                    // 预编译正则表达式，避免在循环中重复编译，提高性能
                    int flags = ignoreCase ? Pattern.CASE_INSENSITIVE : 0;
                    pattern = Pattern.compile(query, flags);
                } catch (Exception e) {
                    throw new IllegalArgumentException("无效的正则表达式: " + e.getMessage());
                }
            } else {
                // 如果不是正则，预先转为小写以支持忽略大小写搜索
                lowerQuery = ignoreCase ? query.toLowerCase() : query;
            }
        }

        // --- 2. 流式文件读取 ---
        // 使用 BufferedReader 逐行读取，内存占用极低
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            int lineNum = 0;
            while ((line = br.readLine()) != null) {
                lineNum++;
                boolean matchQuery = true;
                
                // --- 3. 核心查询匹配 (Query Matching) ---
                if (hasQuery) {
                    if (isRegex) {
                        // 正则匹配
                        matchQuery = pattern.matcher(line).find();
                    } else {
                        // 文本包含匹配
                        if (ignoreCase) {
                            matchQuery = line.toLowerCase().contains(lowerQuery);
                        } else {
                            matchQuery = line.contains(query);
                        }
                    }
                }
                
                // 如果查询不匹配，直接跳过后续过滤，进入下一行
                if (!matchQuery) continue;
                
                // --- 4. 关键字过滤 (Keyword Filtering) ---
                if (hasKeywords) {
                    String checkLine = ignoreCase ? line.toLowerCase() : line;
                    
                    if (matchAllKeywords) {
                        // AND 逻辑：行内容必须包含列表中的【所有】关键字
                        boolean allMatch = true;
                        for (String kw : keywords) {
                             String checkKw = ignoreCase ? kw.toLowerCase() : kw;
                             if (!checkLine.contains(checkKw)) {
                                 allMatch = false;
                                 break; // 只要缺一个，即便失败
                             }
                        }
                        if (!allMatch) continue;
                    } else {
                        // OR 逻辑：行内容只需包含列表中的【任意一个】关键字
                        boolean anyMatch = false;
                        for (String kw : keywords) {
                            String checkKw = ignoreCase ? kw.toLowerCase() : kw;
                            if (checkLine.contains(checkKw)) {
                                anyMatch = true;
                                break; // 只要有一个，即成功
                            }
                        }
                        if (!anyMatch) continue;
                    }
                }

                // 所有条件满足，添加结果
                results.add(new SearchResult(file.getName(), lineNum, line));
            }
        }
        return results;
    }
    
    /**
     * 遗留重载方法 (兼容旧代码)
     * 默认使用 OR 逻辑处理关键字。
     */
    public static List<SearchResult> searchFile(File file, String query, boolean isRegex, boolean ignoreCase, List<String> keywords) throws IOException {
        return searchFile(file, query, isRegex, ignoreCase, keywords, false); 
    }
    
    /**
     * 基础重载方法
     * 仅执行查询搜索，无关键字过滤。
     */
    public static List<SearchResult> searchFile(File file, String query, boolean isRegex, boolean ignoreCase) throws IOException {
        return searchFile(file, query, isRegex, ignoreCase, null);
    }
}
