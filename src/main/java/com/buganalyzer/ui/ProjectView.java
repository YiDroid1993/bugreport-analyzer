package com.buganalyzer.ui;

import com.buganalyzer.model.FileMetadata;
import com.buganalyzer.model.ProjectManifest;
import javafx.geometry.Orientation;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.File;

public class ProjectView extends BorderPane {

    private final ProjectManifest manifest;
    private final String projectPath;
    private final TabPane contentTabs;
    private final ListView<FileMetadata> fileList;
    private final ListView<String> partsList;

    public ProjectView(ProjectManifest manifest, String projectPath) {
        this.manifest = manifest;
        this.projectPath = projectPath;

        // Menu Bar
        MenuBar menuBar = new MenuBar();
        Menu fileMenu = new Menu("文件");
        MenuItem closeItem = new MenuItem("关闭项目");
        closeItem.setOnAction(e -> closeProject());
        fileMenu.getItems().add(closeItem);
        menuBar.getMenus().add(fileMenu);
        setTop(menuBar);

        // Left Sidebar: Files and Parts
        SplitPane sidebarSplit = new SplitPane();
        sidebarSplit.setOrientation(Orientation.VERTICAL);
        
        // File List
        fileList = new ListView<>();
        fileList.getItems().addAll(manifest.getFiles());
        fileList.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(FileMetadata item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getFileName() + " (" + item.getType() + ")");
                }
            }
        });
        
        VBox fileBox = new VBox(new Label("文件列表"), fileList);
        fileBox.setFillWidth(true);
        
        // Parts List
        partsList = new ListView<>();
        VBox partsBox = new VBox(new Label("分卷部分"), partsList);
        partsBox.setFillWidth(true);
        
        sidebarSplit.getItems().addAll(fileBox, partsBox);
        sidebarSplit.setDividerPositions(0.7);

        // Content Area
        contentTabs = new TabPane();

        SplitPane mainSplit = new SplitPane();
        mainSplit.getItems().addAll(sidebarSplit, contentTabs);
        mainSplit.setDividerPositions(0.25);
        setCenter(mainSplit);

        // Event Listeners
        fileList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                openFile(newVal);
                updatePartsList(newVal);
            }
        });
        
        partsList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                Tab selectedTab = contentTabs.getSelectionModel().getSelectedItem();
                if (selectedTab != null && selectedTab.getContent() instanceof TextViewer) {
                    ((TextViewer) selectedTab.getContent()).loadContent(newVal);
                }
            }
        });
        
        // Sync parts list when tab changes
        contentTabs.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab != null) {
                if (newTab.getUserData() instanceof FileMetadata) {
                    FileMetadata fm = (FileMetadata) newTab.getUserData();
                    fileList.getSelectionModel().select(fm); // Sync file list
                    updatePartsList(fm);
                }
            }
        });
    }

    private void openFile(FileMetadata file) {
        // Check if already open
        for (Tab tab : contentTabs.getTabs()) {
            if (tab.getText().equals(file.getFileName())) {
                contentTabs.getSelectionModel().select(tab);
                return;
            }
        }

        Tab tab = new Tab(file.getFileName());
        tab.setUserData(file); // Store metadata for sync
        
        if (file.getType() == FileMetadata.FileType.VIDEO) {
            tab.setContent(new VideoPlayer(file, projectPath).getView());
        } else {
            tab.setContent(new TextViewer(file, projectPath));
        }
        
        contentTabs.getTabs().add(tab);
        contentTabs.getSelectionModel().select(tab);
    }
    
    private void updatePartsList(FileMetadata file) {
        partsList.getItems().clear();
        if (file.getSplitParts() != null && !file.getSplitParts().isEmpty()) {
            for (String part : file.getSplitParts()) {
                if (!part.equals(file.getFileName())) {
                    partsList.getItems().add(part);
                }
            }
        }
    }

    private void closeProject() {
        Stage stage = (Stage) getScene().getWindow();
        stage.setScene(new javafx.scene.Scene(new WelcomeView(stage), 800, 600));
        stage.setTitle("欢迎使用Android Bugreport 分析工具");
    }
}
