package com.buganalyzer.ui;

import com.buganalyzer.core.SearchEngine;
import com.buganalyzer.model.FileMetadata;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Callback;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SearchWindow {

    private final FileMetadata fileMetadata;
    private final String projectPath;
    private final String query;
    private final boolean isRegex;
    private final ListView<String> resultsList;
    private int currentSearchIndex = -1;

    // Local search controls
    private CheckBox regexCheck;
    private CheckBox caseCheck;
    private CheckBox wordCheck;

    // Common style for darker controls
    private static final String CONTROL_STYLE = "-fx-base: #e0e0e0; -fx-text-fill: black; -fx-font-weight: bold; -fx-border-color: #a0a0a0; -fx-border-radius: 3;";

    // Drag selection state
    private int dragAnchorIndex = -1;
    private boolean isDragging = false;

    public SearchWindow(FileMetadata fileMetadata, String projectPath, String query, boolean isRegex) {
        this.fileMetadata = fileMetadata;
        this.projectPath = projectPath;
        this.query = query;
        this.isRegex = isRegex;
        this.resultsList = new ListView<>();
    }

    public void show() {
        Stage stage = new Stage();
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        // Header with Local Search
        VBox topContainer = new VBox(5);
        Label header = new Label("Searching for: " + query);
        header.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        
        HBox localSearchBox = new HBox(10);
        localSearchBox.setPadding(new Insets(5, 0, 5, 0));
        TextField localSearchField = new TextField();
        localSearchField.setPromptText("Filter/Find in results...");
        localSearchField.setStyle("-fx-border-color: #a0a0a0; -fx-border-radius: 3;");
        HBox.setHgrow(localSearchField, Priority.ALWAYS);
        
        regexCheck = new CheckBox("正则");
        caseCheck = new CheckBox("大小写");
        wordCheck = new CheckBox("单词");
        
        // Apply darker style
        regexCheck.setStyle("-fx-text-fill: black; -fx-font-weight: bold;");
        caseCheck.setStyle("-fx-text-fill: black; -fx-font-weight: bold;");
        wordCheck.setStyle("-fx-text-fill: black; -fx-font-weight: bold;");

        Button prevButton = new Button("上一个");
        Button nextButton = new Button("下一个");
        prevButton.setStyle(CONTROL_STYLE);
        nextButton.setStyle(CONTROL_STYLE);
        
        localSearchBox.getChildren().addAll(new Label("Find:"), localSearchField, regexCheck, caseCheck, wordCheck, prevButton, nextButton);
        topContainer.getChildren().addAll(header, localSearchBox);
        root.setTop(topContainer);

        // Results List Styling & Selection
        resultsList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        
        resultsList.setCellFactory(new Callback<>() {
            @Override
            public ListCell<String> call(ListView<String> param) {
                ListCell<String> cell = new ListCell<>() {
                    @Override
                    protected void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty || item == null) {
                            setText(null);
                            setGraphic(null);
                        } else {
                            setText(item);
                            setTextFill(Color.BLACK);
                            setStyle("-fx-font-family: 'Monospaced'; -fx-font-size: 12px;");
                            setWrapText(true);
                            setPrefWidth(0);
                            maxWidthProperty().bind(resultsList.widthProperty().subtract(20));
                        }
                    }
                };

                // Drag Selection Logic
                cell.setOnMousePressed(event -> {
                    if (event.isPrimaryButtonDown()) {
                        isDragging = true;
                        dragAnchorIndex = cell.getIndex();
                        if (!event.isControlDown() && !event.isShiftDown()) {
                            resultsList.getSelectionModel().clearAndSelect(dragAnchorIndex);
                        }
                    }
                });

                cell.setOnDragDetected(event -> {
                    if (event.isPrimaryButtonDown()) {
                        cell.startFullDrag();
                    }
                });

                cell.setOnMouseDragEntered(event -> {
                    if (event.isPrimaryButtonDown() && dragAnchorIndex >= 0) {
                        int currentIndex = cell.getIndex();
                        if (currentIndex >= 0) {
                            if (!event.isControlDown()) {
                                resultsList.getSelectionModel().clearSelection();
                            }
                            int start = Math.min(dragAnchorIndex, currentIndex);
                            int end = Math.max(dragAnchorIndex, currentIndex);
                            // selectRange is exclusive of the end index in some implementations, 
                            // but for ListView it selects indices from start to end inclusive?
                            // Let's use selectIndices for safety or loop.
                            // Actually selectRange(start, end + 1) is standard for "inclusive start, exclusive end".
                            // But ListView's selectRange(int, int) documentation says:
                            // "Selects the indices in the range [start, end)."
                            resultsList.getSelectionModel().selectRange(start, end + 1);
                        }
                    }
                });

                return cell;
            }
        });
        
        // Context Menu
        ContextMenu contextMenu = new ContextMenu();
        MenuItem copyItem = new MenuItem("复制");
        copyItem.setOnAction(e -> copySelection());
        MenuItem selectAllItem = new MenuItem("全选");
        selectAllItem.setOnAction(e -> resultsList.getSelectionModel().selectAll());
        contextMenu.getItems().addAll(copyItem, selectAllItem);
        resultsList.setContextMenu(contextMenu);

        // Key Events (Ctrl+C, Ctrl+A)
        resultsList.setOnKeyPressed(event -> {
            if (event.isControlDown()) {
                if (event.getCode() == KeyCode.C) {
                    copySelection();
                } else if (event.getCode() == KeyCode.A) {
                    resultsList.getSelectionModel().selectAll();
                }
            }
        });
        
        // Remove the old filter as we use FullDrag now
        // resultsList.addEventFilter(MouseEvent.MOUSE_DRAGGED...

        // Scroll Wheel Support during Drag
        resultsList.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (isDragging) {
                // Robustly find vertical scrollbar
                ScrollBar sb = null;
                for (javafx.scene.Node n : resultsList.lookupAll(".scroll-bar")) {
                    if (n instanceof ScrollBar && ((ScrollBar) n).getOrientation() == javafx.geometry.Orientation.VERTICAL) {
                        sb = (ScrollBar) n;
                        break;
                    }
                }
                
                if (sb != null) {
                    double deltaY = event.getDeltaY();
                    if (deltaY > 0) {
                        sb.decrement();
                    } else {
                        sb.increment();
                    }
                    
                    boolean isCtrl = event.isControlDown();
                    // Update selection after layout update
                    Platform.runLater(() -> updateSelectionFromMouse(event.getSceneX(), event.getSceneY(), isCtrl));
                    event.consume();
                }
            }
        });

        root.setCenter(resultsList);
        
        ProgressIndicator progress = new ProgressIndicator();
        root.setBottom(progress);

        // Local Search Logic
        localSearchField.setOnAction(e -> findNext(localSearchField.getText()));
        nextButton.setOnAction(e -> findNext(localSearchField.getText()));
        prevButton.setOnAction(e -> findPrev(localSearchField.getText()));

        Scene scene = new Scene(root, 900, 700);
        // Reset dragging state on release globally
        scene.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> isDragging = false);
        
        stage.setScene(scene);
        stage.setTitle("搜索结果 - " + query);
        stage.show();

        CompletableFuture.runAsync(() -> {
            List<File> filesToSearch = new ArrayList<>();
            File projectDir = new File(projectPath);

            if (fileMetadata.getSplitParts() != null && !fileMetadata.getSplitParts().isEmpty()) {
                boolean partsExist = false;
                for (String part : fileMetadata.getSplitParts()) {
                    File partFile = new File(projectDir, part);
                    if (partFile.exists()) {
                        filesToSearch.add(partFile);
                        partsExist = true;
                    }
                }
                if (!partsExist) {
                     File mainFile = new File(projectDir, fileMetadata.getFileName());
                     if (!mainFile.exists()) {
                         // Fallback: Try parent directory
                         File parentDir = projectDir.getParentFile();
                         if (parentDir != null) {
                             File fallbackFile = new File(parentDir, fileMetadata.getFileName());
                             if (fallbackFile.exists()) {
                                 mainFile = fallbackFile;
                             }
                         }
                     }
                     filesToSearch.add(mainFile);
                }
            } else {
                File mainFile = new File(projectDir, fileMetadata.getFileName());
                if (!mainFile.exists()) {
                     // Fallback: Try parent directory
                     File parentDir = projectDir.getParentFile();
                     if (parentDir != null) {
                         File fallbackFile = new File(parentDir, fileMetadata.getFileName());
                         if (fallbackFile.exists()) {
                             mainFile = fallbackFile;
                         }
                     }
                }
                filesToSearch.add(mainFile);
            }

            List<String> allMatches = new ArrayList<>();
            for (File f : filesToSearch) {
                try {
                    List<SearchEngine.SearchResult> results = SearchEngine.searchFile(f, query, isRegex, true);
                    for (SearchEngine.SearchResult r : results) {
                        allMatches.add("[Line " + r.lineNumber + "] " + r.lineContent.trim());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            Platform.runLater(() -> {
                resultsList.getItems().addAll(allMatches);
                progress.setVisible(false);
                header.setText("Found " + allMatches.size() + " matches for: " + query);
            });
        });
    }

    private void updateSelectionFromMouse(double sceneX, double sceneY, boolean isControlDown) {
        if (dragAnchorIndex < 0) return;
        
        for (javafx.scene.Node node : resultsList.lookupAll(".list-cell")) {
            if (node instanceof ListCell) {
                ListCell<?> cell = (ListCell<?>) node;
                if (cell.isVisible()) {
                    javafx.geometry.Bounds bounds = cell.localToScene(cell.getBoundsInLocal());
                    if (bounds.contains(sceneX, sceneY)) {
                        int index = cell.getIndex();
                        if (index >= 0) {
                            if (!isControlDown) {
                                resultsList.getSelectionModel().clearSelection();
                            }
                            int start = Math.min(dragAnchorIndex, index);
                            int end = Math.max(dragAnchorIndex, index);
                            resultsList.getSelectionModel().selectRange(start, end + 1);
                        }
                        break;
                    }
                }
            }
        }
    }

    private void copySelection() {
        List<String> selected = resultsList.getSelectionModel().getSelectedItems();
        if (selected != null && !selected.isEmpty()) {
            String content = String.join("\n", selected);
            ClipboardContent clipboardContent = new ClipboardContent();
            clipboardContent.putString(content);
            Clipboard.getSystemClipboard().setContent(clipboardContent);
        }
    }

    private void findNext(String text) {
        if (text == null || text.isEmpty()) return;
        int start = currentSearchIndex + 1;
        for (int i = start; i < resultsList.getItems().size(); i++) {
            if (matches(resultsList.getItems().get(i), text)) {
                selectAndScroll(i);
                return;
            }
        }
        // Wrap around
        for (int i = 0; i < start; i++) {
            if (matches(resultsList.getItems().get(i), text)) {
                selectAndScroll(i);
                return;
            }
        }
    }

    private void findPrev(String text) {
        if (text == null || text.isEmpty()) return;
        int start = currentSearchIndex - 1;
        if (start < 0) start = resultsList.getItems().size() - 1;
        
        for (int i = start; i >= 0; i--) {
            if (matches(resultsList.getItems().get(i), text)) {
                selectAndScroll(i);
                return;
            }
        }
        // Wrap around
        for (int i = resultsList.getItems().size() - 1; i > start; i--) {
            if (matches(resultsList.getItems().get(i), text)) {
                selectAndScroll(i);
                return;
            }
        }
    }

    private boolean matches(String content, String searchText) {
        boolean isRegex = regexCheck.isSelected();
        boolean isCaseSensitive = caseCheck.isSelected();
        boolean isWholeWord = wordCheck.isSelected();

        if (isRegex) {
            int flags = isCaseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
            try {
                Pattern p = Pattern.compile(searchText, flags);
                return p.matcher(content).find();
            } catch (Exception e) {
                return false; // Invalid regex
            }
        } else {
            if (isWholeWord) {
                String regex = "\\b" + Pattern.quote(searchText) + "\\b";
                int flags = isCaseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
                return Pattern.compile(regex, flags).matcher(content).find();
            } else {
                if (isCaseSensitive) {
                    return content.contains(searchText);
                } else {
                    return content.toLowerCase().contains(searchText.toLowerCase());
                }
            }
        }
    }

    private void selectAndScroll(int index) {
        currentSearchIndex = index;
        // Clear previous selection for find next? Or keep it? 
        // Usually find next selects a single item.
        resultsList.getSelectionModel().clearSelection();
        resultsList.getSelectionModel().select(index);
        resultsList.scrollTo(index);
    }
}
