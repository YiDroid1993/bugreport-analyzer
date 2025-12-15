package com.buganalyzer.ui;

import com.buganalyzer.core.AISiteManager;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class AIConsultationView extends Stage {

    private final String initialContext;
    private final AISiteManager siteManager;
    private final WebView webView;
    private final ListView<String> contextListView;

    public AIConsultationView(String contextText) {
        this.initialContext = contextText;
        this.siteManager = new AISiteManager();
        setTitle("咨询 AI");

        // Main Layout: SplitPane (Horizontal)
        SplitPane mainSplit = new SplitPane();
        mainSplit.setOrientation(Orientation.HORIZONTAL);

        // --- Left Panel: AI Browser ---
        VBox leftPanel = new VBox(5);
        leftPanel.setPadding(new Insets(5));
        
        // Top Bar: Selector + Add Button
        HBox aiTopBar = new HBox(10);
        aiTopBar.setPadding(new Insets(5));
        
        ComboBox<AISiteManager.AISite> siteSelector = new ComboBox<>();
        siteSelector.getItems().addAll(siteManager.getSites());
        if (!siteManager.getSites().isEmpty()) {
            siteSelector.getSelectionModel().select(0);
        }
        siteSelector.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(siteSelector, Priority.ALWAYS);
        
        Button addSiteButton = new Button("+");
        addSiteButton.setOnAction(e -> showAddSiteDialog(siteSelector));
        
        Button deleteSiteButton = new Button("-");
        deleteSiteButton.setOnAction(e -> {
             AISiteManager.AISite selected = siteSelector.getSelectionModel().getSelectedItem();
             if (selected != null) {
                 siteManager.removeSite(selected);
                 siteSelector.getItems().remove(selected);
             }
        });

        aiTopBar.getChildren().addAll(new Label("AI 模型:"), siteSelector, addSiteButton, deleteSiteButton);

        // WebView
        webView = new WebView();
        VBox.setVgrow(webView, Priority.ALWAYS);
        
        // Load selected site
        siteSelector.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                webView.getEngine().load(newVal.getUrl());
            }
        });
        // Initial load
        if (siteSelector.getSelectionModel().getSelectedItem() != null) {
            webView.getEngine().load(siteSelector.getSelectionModel().getSelectedItem().getUrl());
        }

        leftPanel.getChildren().addAll(aiTopBar, webView);

        // --- Right Panel: Context Text ---
        VBox rightPanel = new VBox(5);
        rightPanel.setPadding(new Insets(5));
        
        Label contextLabel = new Label("选中的上下文:");
        contextListView = new ListView<>();
        contextListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        contextListView.setStyle("-fx-font-family: 'Monospaced'; -fx-font-size: 12px;");
        
        // Populate Context
        if (initialContext != null && !initialContext.isEmpty()) {
            contextListView.getItems().addAll(Arrays.asList(initialContext.split("\n")));
        }

        // Context Menu for Right Panel
        ContextMenu contextMenu = new ContextMenu();
        MenuItem copyItem = new MenuItem("复制");
        copyItem.setOnAction(e -> copySelection());
        MenuItem selectAllItem = new MenuItem("全选");
        selectAllItem.setOnAction(e -> contextListView.getSelectionModel().selectAll());
        contextMenu.getItems().addAll(copyItem, selectAllItem);
        contextListView.setContextMenu(contextMenu);

        // Key Events
        contextListView.setOnKeyPressed(event -> {
            if (event.isControlDown()) {
                if (event.getCode() == KeyCode.C) {
                    copySelection();
                } else if (event.getCode() == KeyCode.A) {
                    contextListView.getSelectionModel().selectAll();
                }
            }
        });

        VBox.setVgrow(contextListView, Priority.ALWAYS);
        rightPanel.getChildren().addAll(contextLabel, contextListView);

        // Assemble SplitPane
        mainSplit.getItems().addAll(leftPanel, rightPanel);
        mainSplit.setDividerPositions(0.6); // 60% for AI, 40% for Text

        Scene scene = new Scene(mainSplit, 1000, 700);
        setScene(scene);
    }

    private void showAddSiteDialog(ComboBox<AISiteManager.AISite> selector) {
        Dialog<AISiteManager.AISite> dialog = new Dialog<>();
        dialog.setTitle("添加 AI 网站");
        dialog.setHeaderText("请输入 AI 网站的名称和 URL");

        ButtonType loginButtonType = new ButtonType("添加", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(loginButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField nameField = new TextField();
        nameField.setPromptText("例如: ChatGPT");
        TextField urlField = new TextField();
        urlField.setPromptText("例如: https://chat.openai.com/");

        grid.add(new Label("名称:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("URL:"), 0, 1);
        grid.add(urlField, 1, 1);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == loginButtonType) {
                return new AISiteManager.AISite(nameField.getText(), urlField.getText());
            }
            return null;
        });

        Optional<AISiteManager.AISite> result = dialog.showAndWait();
        result.ifPresent(site -> {
            siteManager.addSite(site.getName(), site.getUrl());
            selector.getItems().add(site);
            selector.getSelectionModel().select(site);
        });
    }

    private void copySelection() {
        List<String> selected = contextListView.getSelectionModel().getSelectedItems();
        if (selected != null && !selected.isEmpty()) {
            String content = String.join("\n", selected);
            ClipboardContent clipboardContent = new ClipboardContent();
            clipboardContent.putString(content);
            Clipboard.getSystemClipboard().setContent(clipboardContent);
        }
    }
}
