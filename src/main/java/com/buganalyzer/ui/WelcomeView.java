package com.buganalyzer.ui;

import com.buganalyzer.core.ProjectManager;
import com.buganalyzer.core.RecentProjectsManager;
import com.buganalyzer.core.SettingsManager;
import com.buganalyzer.core.ZipExtractor;
import com.buganalyzer.model.ProjectManifest;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import org.apache.commons.io.FileUtils;

public class WelcomeView extends BorderPane {

    private final ProgressIndicator progress;
    private final Label statusLabel;
    private final RecentProjectsManager recentProjectsManager;
    private final SettingsManager settingsManager;
    private final Stage stage;

    public WelcomeView(Stage stage) {
        this.stage = stage;
        recentProjectsManager = new RecentProjectsManager();
        recentProjectsManager.validateProjects(); // Auto-clean invalid projects
        settingsManager = new SettingsManager();
        
        // Default Close Behavior for Welcome View: Exit Application immediately
        this.stage.setOnCloseRequest(e -> {
            Platform.exit();
            System.exit(0);
        });
        
        setPadding(new Insets(20));

        // Center: Drag & Drop and Buttons
        VBox centerBox = new VBox(20);
        centerBox.setAlignment(Pos.CENTER);

        Label titleLabel = new Label("Android BugReport 分析工具");
        titleLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");

        Label dropLabel = new Label("拖拽 .zip 文件到这里");
        dropLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #666;");

        Button openZipButton = new Button("或者打开 .zip 文件 (新建项目)");
        openZipButton.setStyle("-fx-base: #e0e0e0; -fx-text-fill: black; -fx-font-weight: bold; -fx-border-color: #a0a0a0; -fx-border-radius: 3; -fx-font-size: 14px;");
        openZipButton.setOnAction(e -> chooseZipFile());

        Button openProjectButton = new Button("打开 .json 文件 (打开项目)");
        openProjectButton.setStyle("-fx-base: #e0e0e0; -fx-text-fill: black; -fx-font-weight: bold; -fx-border-color: #a0a0a0; -fx-border-radius: 3; -fx-font-size: 14px;");
        openProjectButton.setOnAction(e -> chooseProjectFile());

        progress = new ProgressIndicator();
        progress.setVisible(false);
        
        statusLabel = new Label("");

        centerBox.getChildren().addAll(titleLabel, dropLabel, openZipButton, openProjectButton, progress, statusLabel);
        setCenter(centerBox);
        
        // Right: Recent Projects
        VBox rightBox = new VBox(10);
        rightBox.setPadding(new Insets(0, 0, 0, 20));
        rightBox.setPrefWidth(250);
        rightBox.setStyle("-fx-border-color: #ccc; -fx-border-width: 0 0 0 1; -fx-padding: 10;");
        
        Label recentLabel = new Label("最近项目");
        recentLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        
        ListView<RecentProjectsManager.RecentProject> recentList = new ListView<>();
        recentList.getItems().addAll(recentProjectsManager.getRecentProjects());
        recentList.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(RecentProjectsManager.RecentProject item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setContextMenu(null);
                } else {
                    setText(item.getName());
                    setTooltip(new Tooltip(item.getPath()));
                    
                    ContextMenu contextMenu = new ContextMenu();
                    
                    MenuItem renameItem = new MenuItem("重命名");
                    renameItem.setOnAction(e -> {
                        TextInputDialog dialog = new TextInputDialog(item.getName());
                        dialog.setTitle("重命名项目");
                        dialog.setHeaderText("重命名项目: " + item.getName());
                        dialog.setContentText("请输入新的项目名称:");
                        
                        dialog.showAndWait().ifPresent(newName -> {
                            if (!newName.trim().isEmpty()) {
                                try {
                                    // 1. Update RecentProjectsManager
                                    recentProjectsManager.renameProject(item.getPath(), newName);
                                    
                                    // 2. Update ProjectManifest (JSON)
                                    File jsonFile = new File(item.getPath());
                                    if (jsonFile.exists()) {
                                        ProjectManifest manifest = ProjectManager.loadProject(jsonFile);
                                        manifest.setDisplayName(newName);
                                        
                                        // Determine where to save (logic from confirmAndDeleteProject)
                                        File projectDir = null;
                                        if (jsonFile.getParentFile().getName().equals(manifest.getProjectName())) {
                                            projectDir = jsonFile.getParentFile();
                                        } else {
                                            projectDir = new File(jsonFile.getParent(), manifest.getProjectName());
                                        }
                                        
                                        // Save back to the same JSON file
                                        // ProjectManager.saveProject saves to a directory, constructing filename from projectName.
                                        // We want to overwrite the existing file.
                                        // If we use saveProject, it uses manifest.getProjectName() + ".json".
                                        // Since we didn't change projectName, it should overwrite correctly.
                                        ProjectManager.saveProject(manifest, jsonFile.getParentFile());
                                    }
                                    
                                    // 3. Refresh List
                                    recentList.getItems().clear();
                                    recentList.getItems().addAll(recentProjectsManager.getRecentProjects());
                                    
                                } catch (Exception ex) {
                                    Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                                    errorAlert.setTitle("Error");
                                    errorAlert.setHeaderText("Could not rename project");
                                    errorAlert.setContentText(ex.getMessage());
                                    errorAlert.showAndWait();
                                }
                            }
                        });
                    });
                    
                    MenuItem deleteItem = new MenuItem("删除项目");
                    deleteItem.setOnAction(e -> confirmAndDeleteProject(item));
                    
                    contextMenu.getItems().addAll(renameItem, deleteItem);
                    setContextMenu(contextMenu);
                }
            }
        });
        recentList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                RecentProjectsManager.RecentProject selected = recentList.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    loadProject(new File(selected.getPath()));
                }
            }
        });
        
        rightBox.getChildren().addAll(recentLabel, recentList);
        setRight(rightBox);

        // Bottom: Settings
        HBox bottomBox = new HBox();
        bottomBox.setAlignment(Pos.BOTTOM_RIGHT);
        bottomBox.setPadding(new Insets(10));
        
        Button settingsButton = new Button("设置");
        settingsButton.setStyle("-fx-base: #e0e0e0; -fx-text-fill: black; -fx-font-weight: bold; -fx-border-color: #a0a0a0; -fx-border-radius: 3;");
        settingsButton.setOnAction(e -> showSettingsDialog());
        
        bottomBox.getChildren().add(settingsButton);
        setBottom(bottomBox);


        // Drag and Drop Support
        setOnDragOver(event -> {
            if (event.getGestureSource() != this && event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
            }
            event.consume();
        });

        setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                File file = db.getFiles().get(0);
                if (file.getName().endsWith(".zip")) {
                    processZipFile(file);
                    success = true;
                }
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    private void chooseZipFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("选择 BugReport Zip");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Zip Files", "*.zip"));
        
        String defaultDir = settingsManager.getDefaultOpenDirectory();
        if (defaultDir != null) {
            File dir = new File(defaultDir);
            if (dir.exists()) fileChooser.setInitialDirectory(dir);
        }
        
        File file = fileChooser.showOpenDialog(getScene().getWindow());
        if (file != null) {
            processZipFile(file);
        }
    }

    private void chooseProjectFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("选择项目清单");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files", "*.json"));
        
        String defaultDir = settingsManager.getDefaultOpenDirectory();
        if (defaultDir != null) {
            File dir = new File(defaultDir);
            if (dir.exists()) fileChooser.setInitialDirectory(dir);
        }

        File file = fileChooser.showOpenDialog(getScene().getWindow());
        if (file != null) {
            loadProject(file);
        }
    }

    private void processZipFile(File zipFile) {
        progress.setVisible(true);
        statusLabel.setText("正在解压并处理 " + zipFile.getName() + "...");
        
        CompletableFuture.runAsync(() -> {
            try {
                ZipExtractor extractor = new ZipExtractor();
                ProjectManifest manifest = ZipExtractor.extractProject(zipFile);
                
                // Save JSON INSIDE the project directory
                File projectDir = new File(new File(manifest.getOriginalZipPath()).getParent(), manifest.getProjectName());
                ProjectManager.saveProject(manifest, projectDir);
                
                Platform.runLater(() -> {
                    progress.setVisible(false);
                    // JSON path is now projectDir/ProjectName.json
                    File jsonFile = new File(projectDir, manifest.getProjectName() + ".json");
                    recentProjectsManager.addProject(manifest.getProjectName(), jsonFile.getAbsolutePath());
                    openProjectView(manifest, projectDir.getAbsolutePath());
                });
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    progress.setVisible(false);
                    statusLabel.setText("错误: " + e.getMessage());
                    Alert alert = new Alert(Alert.AlertType.ERROR, "处理 zip 失败: " + e.getMessage());
                    alert.showAndWait();
                });
            }
        });
    }

    private void loadProject(File jsonFile) {
        try {
            ProjectManifest manifest = ProjectManager.loadProject(jsonFile);
            
            File projectDir;
            // Check if the parent directory name matches the project name (New Structure)
            // Structure: .../ProjectName/ProjectName.json
            if (jsonFile.getParentFile().getName().equals(manifest.getProjectName())) {
                projectDir = jsonFile.getParentFile();
            } else {
                // Fallback to Old Structure: .../ProjectName.json (Sibling is .../ProjectName/)
                projectDir = new File(jsonFile.getParentFile(), manifest.getProjectName());
            }
            
            String display = manifest.getDisplayName();
            if (display == null || display.isEmpty()) {
                display = manifest.getProjectName();
            }
            recentProjectsManager.addProject(display, jsonFile.getAbsolutePath());
            openProjectView(manifest, projectDir.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
            // statusLabel.setText("加载项目错误: " + e.getMessage()); // Suppressed as per user request
            
            // Show Error Alert
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("加载失败");
            alert.setHeaderText("无法打开项目");
            alert.setContentText("项目加载失败，可能文件已损坏或不存在。\n\n错误信息: " + e.getMessage() + "\n\n该项目将从最近项目列表中移除。");
            alert.showAndWait();
            
            // Remove from Recent List
            if (jsonFile != null) {
                recentProjectsManager.removeProject(jsonFile.getAbsolutePath());
                
                // Refresh List UI
                ListView<RecentProjectsManager.RecentProject> list = (ListView<RecentProjectsManager.RecentProject>) ((VBox) getRight()).getChildren().get(1);
                list.getItems().clear();
                list.getItems().addAll(recentProjectsManager.getRecentProjects());
            }
        }
    }

    private void openProjectView(ProjectManifest manifest, String projectPath) {
        Stage stage = (Stage) getScene().getWindow();
        stage.setScene(new Scene(new ProjectView(manifest, projectPath), 1200, 800));
        stage.setTitle("Bug Analyzer - " + manifest.getProjectName());
        
        // Custom Close Behavior for Project View: Confirm and Return to Welcome
        stage.setOnCloseRequest(event -> {
            event.consume(); // Prevent default close
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("关闭项目");
            alert.setHeaderText("关闭项目");
            alert.setContentText("您确定要关闭此项目并返回欢迎界面吗？");
            
            alert.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    // Switch back to WelcomeView
                    stage.setScene(new Scene(new WelcomeView(stage), 800, 600));
                    stage.setTitle("欢迎 - Bug Analyzer");
                    // The WelcomeView constructor will reset the close handler to Exit!
                }
            });
        });
    }
    
    private void showSettingsDialog() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("设置");
        dialog.setHeaderText("应用设置");
        
        ButtonType saveButtonType = new ButtonType("保存", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);
        
        VBox content = new VBox(10);
        content.setPadding(new Insets(20));
        
        Label dirLabel = new Label("默认打开目录:");
        TextField dirField = new TextField(settingsManager.getDefaultOpenDirectory());
        Button browseButton = new Button("浏览路径");
        browseButton.setOnAction(e -> {
            DirectoryChooser dc = new DirectoryChooser();
            File f = dc.showDialog(dialog.getOwner());
            if (f != null) {
                dirField.setText(f.getAbsolutePath());
            }
        });
        
        HBox dirBox = new HBox(10, dirField, browseButton);
        content.getChildren().addAll(dirLabel, dirBox);
        
        dialog.getDialogPane().setContent(content);
        
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                settingsManager.setDefaultOpenDirectory(dirField.getText());
            }
            return null;
        });
        
        dialog.showAndWait();
    }

    private void confirmAndDeleteProject(RecentProjectsManager.RecentProject project) {
        boolean canMoveToTrash = java.awt.Desktop.isDesktopSupported() && 
                                 java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.MOVE_TO_TRASH);

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("删除项目");
        alert.setHeaderText("删除项目: " + project.getName());
        
        if (canMoveToTrash) {
            alert.setContentText("确定要删除此项目吗？\n这将把项目目录移动到回收站/废纸篓：\n" + project.getPath());
        } else {
            alert.setContentText("确定要删除此项目吗？\n警告：这将永久删除该目录，无法撤销：\n" + project.getPath());
        }

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    File jsonFile = new File(project.getPath());
                    File projectDir = null;
                    
                    // Determine Project Directory
                    if (jsonFile.getParentFile().getName().equals(project.getName())) {
                        projectDir = jsonFile.getParentFile();
                    } else {
                        String jsonName = jsonFile.getName();
                        if (jsonName.toLowerCase().endsWith(".json")) {
                            String projectName = jsonName.substring(0, jsonName.length() - 5);
                            projectDir = new File(jsonFile.getParent(), projectName);
                        }
                    }
                    
                    // CRITICAL SAFETY CHECK
                    String userHome = System.getProperty("user.home");
                    boolean isUnsafe = false;
                    if (projectDir != null) {
                        String absPath = projectDir.getAbsolutePath();
                        if (absPath.equals(userHome) || 
                            absPath.equals(userHome + File.separator + "Downloads") ||
                            absPath.equals(userHome + File.separator + "Desktop") ||
                            absPath.equals(userHome + File.separator + "Documents") ||
                            absPath.equals(userHome + File.separator + "下载") || 
                            projectDir.getParent() == null) {
                            isUnsafe = true;
                        }
                    }
                    
                    if (jsonFile.getParent() == null || jsonFile.getParent().equals(userHome)) {
                         // Safety check for JSON file parent
                    }

                    if (isUnsafe) {
                        final String unsafePath = (projectDir != null) ? projectDir.getAbsolutePath() : "null";
                        Platform.runLater(() -> {
                            Alert safetyAlert = new Alert(Alert.AlertType.WARNING);
                            safetyAlert.setTitle("安全警告");
                            safetyAlert.setHeaderText("删除被阻止");
                            safetyAlert.setContentText("应用程序阻止了对受保护目录的删除：\n" + unsafePath);
                            safetyAlert.showAndWait();
                        });
                        return;
                    }

                    // EXECUTE DELETION
                    if (canMoveToTrash) {
                        boolean dirDeleted = false;
                        boolean jsonDeleted = false;
                        
                        if (projectDir != null && projectDir.exists() && projectDir.isDirectory()) {
                            dirDeleted = java.awt.Desktop.getDesktop().moveToTrash(projectDir);
                        }
                        
                        if (jsonFile.exists()) {
                            jsonDeleted = java.awt.Desktop.getDesktop().moveToTrash(jsonFile);
                        }
                        
                        if (!dirDeleted && projectDir != null && projectDir.exists()) {
                             throw new IOException("移动项目目录到回收站失败。");
                        }
                    } else {
                        // FALLBACK: PERMANENT DELETE
                        if (projectDir != null && projectDir.exists() && projectDir.isDirectory()) {
                            FileUtils.deleteDirectory(projectDir);
                        }
                        if (jsonFile.exists()) {
                            jsonFile.delete();
                        }
                    }
                    
                    // Remove from list
                    recentProjectsManager.removeProject(project.getPath());
                    ListView<RecentProjectsManager.RecentProject> list = (ListView<RecentProjectsManager.RecentProject>) ((VBox) getRight()).getChildren().get(1);
                    list.getItems().remove(project);
                    
                } catch (Exception ex) {
                    Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                    errorAlert.setTitle("错误");
                    errorAlert.setHeaderText("无法删除项目");
                    errorAlert.setContentText(ex.getMessage());
                    errorAlert.showAndWait();
                }
            }
        });
    }
}
