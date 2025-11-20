package com.buganalyzer.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class FileSplitter {

    public static List<String> splitFile(File sourceFile) throws IOException {
        long fileSize = sourceFile.length();
        if (fileSize < 10 * 1024 * 1024) { // Less than 10MB
            return null;
        }

        long sizeMB = fileSize / (1024 * 1024);
        
        // Rule: 10-100 parts. 
        // < 100MB -> 10 parts
        // > 1000MB -> 100 parts
        // Linear in between: part count = sizeMB / 10
        int parts = (int) (sizeMB / 10);
        if (parts < 10) parts = 10;
        if (parts > 100) parts = 100;

        List<String> generatedFiles = new ArrayList<>();
        long bytesPerPart = fileSize / parts;
        
        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(sourceFile))) {
            String baseName = sourceFile.getName();
            String parentPath = sourceFile.getParent();
            // Remove extension for base name
            int dotIndex = baseName.lastIndexOf('.');
            String nameOnly = (dotIndex == -1) ? baseName : baseName.substring(0, dotIndex);
            String extension = (dotIndex == -1) ? "" : baseName.substring(dotIndex);

            byte[] buffer = new byte[8192];
            
            for (int i = 1; i <= parts; i++) {
                String partName = nameOnly + "_sub" + i + extension;
                File partFile = new File(parentPath, partName);
                generatedFiles.add(partName);

                try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(partFile))) {
                    long bytesWritten = 0;
                    // For the last part, write everything remaining
                    long limit = (i == parts) ? (fileSize - (bytesPerPart * (parts - 1))) : bytesPerPart;
                    
                    while (bytesWritten < limit) {
                        int maxRead = (int) Math.min(buffer.length, limit - bytesWritten);
                        int read = bis.read(buffer, 0, maxRead);
                        if (read == -1) break;
                        bos.write(buffer, 0, read);
                        bytesWritten += read;
                    }
                }
            }
        }
        
        return generatedFiles;
    }
}
