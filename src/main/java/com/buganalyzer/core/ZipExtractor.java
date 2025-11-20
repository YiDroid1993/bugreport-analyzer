package com.buganalyzer.core;

import com.buganalyzer.model.FileMetadata;
import com.buganalyzer.model.ProjectManifest;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Pattern;

public class ZipExtractor {

    private static final Pattern BUGREPORT_PATTERN = Pattern.compile("bugreport.*\\.txt", Pattern.CASE_INSENSITIVE);
    private static final Pattern VIDEO_PATTERN = Pattern.compile(".*\\.mp4", Pattern.CASE_INSENSITIVE);

    public static ProjectManifest extractProject(File zipFile) throws IOException {
        String zipName = zipFile.getName();
        String projectName = FilenameUtils.getBaseName(zipName);
        File projectDir = new File(zipFile.getParent(), projectName);
        
        if (!projectDir.exists()) {
            projectDir.mkdirs();
        }

        ProjectManifest manifest = new ProjectManifest(projectName, zipFile.getAbsolutePath());
        
        processZip(zipFile, projectDir, manifest, "");
        
        return manifest;
    }

    private static void processZip(File zipFile, File projectDir, ProjectManifest manifest, String pathPrefix) throws IOException {
        try (ZipFile zip = new ZipFile(zipFile)) {
            Enumeration<ZipArchiveEntry> entries = zip.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                String entryName = entry.getName();
                String fullPathInZip = pathPrefix + entryName;

                if (entry.isDirectory()) {
                    continue;
                }

                // Check if it's a nested zip
                if (entryName.toLowerCase().endsWith(".zip")) {
                    // Extract nested zip to temp file and process recursively
                    File tempZip = File.createTempFile("nested_", ".zip", projectDir);
                    try (InputStream is = zip.getInputStream(entry);
                         OutputStream os = new FileOutputStream(tempZip)) {
                        IOUtils.copy(is, os);
                    }
                    processZip(tempZip, projectDir, manifest, fullPathInZip + "/");
                    tempZip.delete(); // Cleanup temp zip
                    continue;
                }

                FileMetadata.FileType type = null;
                if (BUGREPORT_PATTERN.matcher(new File(entryName).getName()).matches()) {
                    type = FileMetadata.FileType.BUGREPORT;
                } else if (VIDEO_PATTERN.matcher(new File(entryName).getName()).matches()) {
                    type = FileMetadata.FileType.VIDEO;
                }

                if (type != null) {
                    // Extract relevant file
                    String safeName = new File(entryName).getName();
                    // Handle potential duplicates by prefixing/suffixing if needed? 
                    // For now, let's assume unique enough or overwrite. 
                    // Better: preserve some structure or rename.
                    // Requirement: "存入zip压缩文件所在目录的同名文件夹内"
                    // Let's just put them in root of project dir for simplicity as per req 1.
                    
                    File targetFile = new File(projectDir, safeName);
                    // If exists, append timestamp or index to avoid overwrite
                    int index = 1;
                    while (targetFile.exists()) {
                        String base = FilenameUtils.getBaseName(safeName);
                        String ext = FilenameUtils.getExtension(safeName);
                        targetFile = new File(projectDir, base + "_" + index++ + "." + ext);
                    }

                    try (InputStream is = zip.getInputStream(entry);
                         OutputStream os = new FileOutputStream(targetFile)) {
                        IOUtils.copy(is, os);
                    }

                    FileMetadata metadata = new FileMetadata(
                            targetFile.getName(),
                            targetFile.length(),
                            targetFile.getName(), // relative path in project dir
                            fullPathInZip,
                            type
                    );

                    // If bugreport, split it
                    if (type == FileMetadata.FileType.BUGREPORT) {
                        List<String> parts = FileSplitter.splitFile(targetFile);
                        metadata.setSplitParts(parts);
                    }

                    manifest.addFile(metadata);
                }
            }
        }
    }
}
