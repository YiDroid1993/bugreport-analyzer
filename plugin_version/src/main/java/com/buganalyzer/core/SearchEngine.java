package com.buganalyzer.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SearchEngine {

    public static class SearchResult {
        public String filePath;
        public int lineNumber;
        public String lineContent;

        public SearchResult(String filePath, int lineNumber, String lineContent) {
            this.filePath = filePath;
            this.lineNumber = lineNumber;
            this.lineContent = lineContent;
        }
    }

    public static List<SearchResult> searchFile(File file, String query, boolean isRegex, boolean ignoreCase) throws IOException {
        List<SearchResult> results = new ArrayList<>();
        Pattern pattern = null;
        String lowerQuery = null;

        if (isRegex) {
            int flags = ignoreCase ? Pattern.CASE_INSENSITIVE : 0;
            pattern = Pattern.compile(query, flags);
        } else {
            lowerQuery = ignoreCase ? query.toLowerCase() : query;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            int lineNum = 0;
            while ((line = br.readLine()) != null) {
                lineNum++;
                boolean match = false;
                if (isRegex) {
                    match = pattern.matcher(line).find();
                } else {
                    if (ignoreCase) {
                        match = line.toLowerCase().contains(lowerQuery);
                    } else {
                        match = line.contains(query);
                    }
                }

                if (match) {
                    results.add(new SearchResult(file.getName(), lineNum, line));
                }
            }
        }
        return results;
    }
    
    // Simple Logcat parser: "tag:ActivityManager level:E"
    // This is a simplified version. Real logcat parsing is complex.
    // We will treat "tag:X" as "search for X" but maybe refine if needed.
    // For now, let's assume the user types regex or plain text. 
    // If they type "ActivityManager", it finds it.
}
