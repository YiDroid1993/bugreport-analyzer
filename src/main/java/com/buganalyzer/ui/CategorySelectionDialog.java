package com.buganalyzer.ui;

import com.buganalyzer.core.KeywordManager;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CategorySelectionDialog extends Dialog<List<String>> {

    private final KeywordManager keywordManager;
    private final ObservableList<CategoryItem> categories;

    public CategorySelectionDialog(KeywordManager keywordManager) {
        this.keywordManager = keywordManager;
        this.categories = FXCollections.observableArrayList();

        setTitle("选择搜索分类");
        setHeaderText("请选择要搜索的关键字分类");
        setResizable(true);

        ButtonType searchButtonType = new ButtonType("搜索", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(searchButtonType, ButtonType.CANCEL);

        VBox content = new VBox(10);
        content.setPadding(new Insets(20));
        content.setPrefSize(400, 300);

        Label label = new Label("可用分类:");
        
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
        
        CheckBox selectAll = new CheckBox("全选");
        selectAll.setOnAction(e -> {
            boolean selected = selectAll.isSelected();
            for (CategoryItem item : categories) {
                item.setSelected(selected);
            }
        });

        content.getChildren().addAll(label, selectAll, categoryListView);
        getDialogPane().setContent(content);

        // Load Categories
        Map<String, List<String>> map = keywordManager.getCategorizedKeywords();
        for (String cat : map.keySet()) {
            categories.add(new CategoryItem(cat));
        }

        setResultConverter(dialogButton -> {
            if (dialogButton == searchButtonType) {
                return categories.stream()
                        .filter(CategoryItem::isSelected)
                        .map(CategoryItem::getName)
                        .collect(Collectors.toList());
            }
            return null;
        });
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
