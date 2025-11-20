package com.buganalyzer.ui;

import com.buganalyzer.core.KeywordManager;
import com.buganalyzer.core.SearchEngine;
import com.buganalyzer.model.FileMetadata;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextViewer extends BorderPane {

    private final FileMetadata fileMetadata;
    private final String projectPath;
    private final ListView<String> listView;
    private final TextField searchField;
    private final Label statusLabel;

    public TextViewer(FileMetadata fileMetadata, String projectPath) {
        this.fileMetadata = fileMetadata;
        this.projectPath = projectPath;

        // Top Bar: Search and Keywords
        HBox topBar = new HBox(10);
        topBar.setPadding(new Insets(5));
        
        searchField = new TextField();
        searchField.setPromptText("在当前文件中搜索...");
        HBox.setHgrow(searchField, Priority.ALWAYS);
        
        CheckBox regexCheck = new CheckBox("正则");
        regexCheck.setStyle("-fx-text-fill: black; -fx-font-weight: bold;");
        
        Button searchButton = new Button("搜索全部");
        searchButton.setStyle("-fx-base: #e0e0e0; -fx-text-fill: black; -fx-font-weight: bold; -fx-border-color: #a0a0a0; -fx-border-radius: 3;");
        searchButton.setOnAction(e -> performSearch(searchField.getText(), regexCheck.isSelected()));

        Button keywordsButton = new Button("关键字");
        keywordsButton.setStyle("-fx-base: #e0e0e0; -fx-text-fill: black; -fx-font-weight: bold; -fx-border-color: #a0a0a0; -fx-border-radius: 3;");
        keywordsButton.setOnAction(e -> openKeywordDialog());
        
        Button searchKeywordsButton = new Button("搜索关键字");
        searchKeywordsButton.setStyle("-fx-base: #e0e0e0; -fx-text-fill: black; -fx-font-weight: bold; -fx-border-color: #a0a0a0; -fx-border-radius: 3;");
        searchKeywordsButton.setOnAction(e -> {
            KeywordManager km = new KeywordManager();
            if (km.getCategorizedKeywords().isEmpty()) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION, "没有可用的关键字分类。请先在“关键字”管理中添加。");
                alert.showAndWait();
                return;
            }
            
            CategorySelectionDialog dialog = new CategorySelectionDialog(km);
            dialog.showAndWait().ifPresent(selectedCategories -> {
                if (selectedCategories.isEmpty()) return;
                
                List<String> allKeywords = new java.util.ArrayList<>();
                for (String cat : selectedCategories) {
                    List<String> kws = km.getCategorizedKeywords().get(cat);
                    if (kws != null) {
                        allKeywords.addAll(kws);
                    }
                }
                
                if (allKeywords.isEmpty()) {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION, "选中的分类中没有关键字。");
                    alert.showAndWait();
                    return;
                }
                
                String query = String.join("|", allKeywords);
                new SearchWindow(fileMetadata, projectPath, query, true).show();
            });
        });

        topBar.getChildren().addAll(searchField, regexCheck, searchButton, keywordsButton, searchKeywordsButton);
        setTop(topBar);

        // Center: ListView (Virtualized)
        listView = new ListView<>();
        listView.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item);
                    setFont(Font.font("Monospaced", 12));
                    // Optional: Add simple wrapping if needed, but usually logs are better without wrapping or with horizontal scroll
                    // setWrapText(true); 
                }
            }
        });
        setCenter(listView);

        // Bottom: Status
        statusLabel = new Label("已就绪");
        statusLabel.setPadding(new Insets(5));
        setBottom(statusLabel);

        // Load initial content (first part or full file)
        loadContent(null);
    }

    public void loadContent(String partName) {
        statusLabel.setText("加载中...");
        CompletableFuture.runAsync(() -> {
            File resolvedFile = null;
            try {
                
                // Helper to resolve file with parent fallback
                java.util.function.Function<String, File> resolve = name -> {
                    File f = new File(projectPath, name);
                    if (f.exists()) return f;
                    File parent = new File(projectPath).getParentFile();
                    if (parent != null) {
                        File f2 = new File(parent, name);
                        if (f2.exists()) return f2;
                    }
                    return f; // Return original even if not exists, to let downstream handle it
                };

                if (partName != null) {
                    resolvedFile = resolve.apply(partName);
                } else {
                    // Default to first part if split, or the file itself
                    if (fileMetadata.getSplitParts() != null && !fileMetadata.getSplitParts().isEmpty()) {
                        // Try to find the first part
                        File partFile = resolve.apply(fileMetadata.getSplitParts().get(0));
                        if (partFile.exists()) {
                            resolvedFile = partFile;
                        } else {
                            // Fallback to main file if split part missing
                            resolvedFile = resolve.apply(fileMetadata.getFileName());
                        }
                    } else {
                        resolvedFile = resolve.apply(fileMetadata.getFileName());
                    }
                }

                File finalFileToLoad = resolvedFile;
                boolean exists = finalFileToLoad != null && finalFileToLoad.exists();
                String debugInfo = "Proj: " + projectPath + " | File: " + (finalFileToLoad != null ? finalFileToLoad.getName() : "null") + " | Exists: " + exists;
                
                if (!exists) {
                    throw new IOException("File not found. " + debugInfo);
                }

                // Read lines with fallback for encoding issues
                List<String> lines;
                try {
                    lines = FileUtils.readLines(finalFileToLoad, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    // Fallback to ISO-8859-1 if UTF-8 fails (e.g. "Input length = 1" MalformedInputException)
                    System.err.println("UTF-8 read failed, falling back to ISO-8859-1: " + e.getMessage());
                    lines = FileUtils.readLines(finalFileToLoad, StandardCharsets.ISO_8859_1);
                }
                
                List<String> finalLines = lines;
                Platform.runLater(() -> {
                    listView.getItems().setAll(finalLines);
                    statusLabel.setText("已加载: " + finalFileToLoad.getAbsolutePath() + " (" + finalLines.size() + " 行)");
                    highlightKeywords();
                });
            } catch (IOException e) {
                File errorFile = resolvedFile;
                Platform.runLater(() -> statusLabel.setText("错误: " + e.getMessage()));
            }
        });
    }

    private void performSearch(String query, boolean isRegex) {
        if (query == null || query.isEmpty()) return;
        new SearchWindow(fileMetadata, projectPath, query, isRegex).show();
    }

    private void openKeywordDialog() {
        KeywordDialog dialog = new KeywordDialog(new KeywordManager());
        dialog.showAndWait();
        highlightKeywords();
    }

    private void highlightKeywords() {
        // Highlighting in ListView is done via CellFactory if needed.
        // For now, we rely on SearchWindow.
        // To implement highlighting here, we would need to update the CellFactory to parse the text and use a TextFlow.
        listView.refresh(); // Trigger cell update
    }
}
