package com.buganalyzer.ui;

import com.buganalyzer.core.KeywordManager;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Callback;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class KeywordDialog extends Dialog<Void> {

    private final KeywordManager keywordManager;
    private final ObservableList<CategoryItem> categories;
    private final TextArea keywordArea;
    private final Label currentCategoryLabel;
    private String currentCategory = null;

    public KeywordDialog(KeywordManager keywordManager) {
        this.keywordManager = keywordManager;
        this.categories = FXCollections.observableArrayList();

        setTitle("关键字管理");
        setHeaderText(null);
        setResizable(true);

        // Main Layout
        SplitPane splitPane = new SplitPane();
        splitPane.setPrefSize(600, 400);

        // --- Left Side: Categories ---
        VBox leftPane = new VBox(5);
        leftPane.setPadding(new Insets(10));
        
        // Toolbar
        HBox leftToolbar = new HBox(5);
        Button addCategoryBtn = new Button("+");
        Button deleteCategoryBtn = new Button("-");
        leftToolbar.getChildren().addAll(addCategoryBtn, deleteCategoryBtn);
        
        // List
        ListView<CategoryItem> categoryListView = new ListView<>(categories);
        categoryListView.setCellFactory(CheckBoxListCell.forListView(CategoryItem::selectedProperty, new StringConverter<CategoryItem>() {
            @Override
            public String toString(CategoryItem object) {
                return object.getName();
            }
            @Override
            public CategoryItem fromString(String string) {
                return null;
            }
        }));
        VBox.setVgrow(categoryListView, Priority.ALWAYS);
        
        leftPane.getChildren().addAll(new Label("分类列表"), leftToolbar, categoryListView);

        // --- Right Side: Keywords ---
        VBox rightPane = new VBox(10);
        rightPane.setPadding(new Insets(10));
        
        currentCategoryLabel = new Label("请选择一个分类");
        currentCategoryLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        
        keywordArea = new TextArea();
        keywordArea.setWrapText(true);
        keywordArea.setPromptText("在此输入关键字，用分号 (;) 分隔...");
        keywordArea.setDisable(true);
        VBox.setVgrow(keywordArea, Priority.ALWAYS);
        
        Button saveKeywordsBtn = new Button("保存当前分类关键字");
        saveKeywordsBtn.setDisable(true);
        
        rightPane.getChildren().addAll(currentCategoryLabel, keywordArea, saveKeywordsBtn);

        splitPane.getItems().addAll(leftPane, rightPane);
        splitPane.setDividerPositions(0.35);
        
        getDialogPane().setContent(splitPane);
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        // --- Logic ---

        // Load Categories
        refreshCategories();

        // Selection Listener
        categoryListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                currentCategory = newVal.getName();
                currentCategoryLabel.setText("分类: " + currentCategory);
                keywordArea.setDisable(false);
                saveKeywordsBtn.setDisable(false);
                
                List<String> keywords = keywordManager.getCategorizedKeywords().get(currentCategory);
                keywordArea.setText(keywords != null ? String.join("; ", keywords) : "");
            } else {
                currentCategory = null;
                currentCategoryLabel.setText("请选择一个分类");
                keywordArea.setDisable(true);
                saveKeywordsBtn.setDisable(true);
                keywordArea.clear();
            }
        });

        // Add Category
        addCategoryBtn.setOnAction(e -> {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("新建分类");
            dialog.setHeaderText("输入新分类名称");
            dialog.setContentText("名称:");
            dialog.showAndWait().ifPresent(name -> {
                if (!name.trim().isEmpty()) {
                    keywordManager.addCategory(name.trim());
                    refreshCategories();
                    // Select the new category
                    for (CategoryItem item : categories) {
                        if (item.getName().equals(name.trim())) {
                            categoryListView.getSelectionModel().select(item);
                            break;
                        }
                    }
                }
            });
        });

        // Delete Category
        deleteCategoryBtn.setOnAction(e -> {
            List<CategoryItem> toRemove = categories.stream()
                    .filter(CategoryItem::isSelected)
                    .collect(Collectors.toList());
            
            if (toRemove.isEmpty()) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION, "请先勾选要删除的分类");
                alert.showAndWait();
                return;
            }

            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "确定要删除选中的 " + toRemove.size() + " 个分类吗？");
            confirm.showAndWait().ifPresent(resp -> {
                if (resp == ButtonType.OK) {
                    for (CategoryItem item : toRemove) {
                        keywordManager.removeCategory(item.getName());
                    }
                    refreshCategories();
                }
            });
        });

        // Save Keywords
        saveKeywordsBtn.setOnAction(e -> {
            if (currentCategory != null) {
                String text = keywordArea.getText();
                List<String> newKeywords = Arrays.stream(text.split(";"))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());
                keywordManager.updateCategory(currentCategory, newKeywords);
                Alert alert = new Alert(Alert.AlertType.INFORMATION, "保存成功");
                alert.showAndWait();
            }
        });
    }

    private void refreshCategories() {
        categories.clear();
        Map<String, List<String>> map = keywordManager.getCategorizedKeywords();
        for (String cat : map.keySet()) {
            categories.add(new CategoryItem(cat));
        }
    }

    public static class CategoryItem {
        private final StringProperty name = new SimpleStringProperty();
        private final BooleanProperty selected = new SimpleBooleanProperty(false);

        public CategoryItem(String name) {
            this.name.set(name);
        }

        public String getName() { return name.get(); }
        public StringProperty nameProperty() { return name; }
        public boolean isSelected() { return selected.get(); }
        public void setSelected(boolean selected) { this.selected.set(selected); }
        public BooleanProperty selectedProperty() { return selected; }
    }
}
