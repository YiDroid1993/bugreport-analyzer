package com.yidroid.buganalyzer.plugin.ui;

import com.yidroid.buganalyzer.core.KeywordManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class KeywordDialog extends DialogWrapper {

    private final KeywordManager keywordManager;
    private final DefaultListModel<String> categoriesModel;
    private final JBList<String> categoriesList;
    private final JBTextArea keywordArea;
    private final JLabel currentCategoryLabel;
    private String currentCategory = null;

    protected KeywordDialog(Component parent, KeywordManager keywordManager) {
        super(parent, true);
        this.keywordManager = keywordManager;
        setTitle("Keyword Management");
        
        categoriesModel = new DefaultListModel<>();
        categoriesList = new JBList<>(categoriesModel);
        categoriesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        
        keywordArea = new JBTextArea(10, 40);
        keywordArea.setLineWrap(true);
        keywordArea.setWrapStyleWord(true);
        keywordArea.setEnabled(false);
        
        currentCategoryLabel = new JLabel("Please select a category");

        init();
        loadCategories();
        
        categoriesList.addListSelectionListener(e -> onCategorySelected());
    }

    @Override
    protected JComponent createCenterPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        
        // Left: Categories
        JPanel leftPanel = new JPanel(new BorderLayout(5, 5));
        
        JPanel leftTools = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        JButton addBtn = new JButton("+");
        addBtn.addActionListener(e -> addCategory());
        JButton removeBtn = new JButton("-");
        removeBtn.addActionListener(e -> removeCategory());
        leftTools.add(addBtn);
        leftTools.add(removeBtn);
        
        leftPanel.add(new JLabel("Categories"), BorderLayout.NORTH);
        leftPanel.add(new JBScrollPane(categoriesList), BorderLayout.CENTER);
        leftPanel.add(leftTools, BorderLayout.SOUTH);
        leftPanel.setPreferredSize(new Dimension(200, 300));
        
        // Right: Keywords
        JPanel rightPanel = new JPanel(new BorderLayout(5, 5));
        rightPanel.add(currentCategoryLabel, BorderLayout.NORTH);
        rightPanel.add(new JBScrollPane(keywordArea), BorderLayout.CENTER);
        
        JButton saveBtn = new JButton("Save Keywords");
        saveBtn.addActionListener(e -> saveKeywords());
        JPanel rightTools = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        rightTools.add(saveBtn);
        rightPanel.add(rightTools, BorderLayout.SOUTH);
        
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        splitPane.setDividerLocation(200);
        
        mainPanel.add(splitPane, BorderLayout.CENTER);
        return mainPanel;
    }

    private void loadCategories() {
        categoriesModel.clear();
        Map<String, List<String>> map = keywordManager.getCategorizedKeywords();
        for (String cat : map.keySet()) {
            categoriesModel.addElement(cat);
        }
    }

    private void onCategorySelected() {
        currentCategory = categoriesList.getSelectedValue();
        if (currentCategory != null) {
            currentCategoryLabel.setText("Category: " + currentCategory);
            keywordArea.setEnabled(true);
            List<String> keywords = keywordManager.getCategorizedKeywords().get(currentCategory);
            keywordArea.setText(keywords != null ? String.join("; ", keywords) : "");
        } else {
            currentCategoryLabel.setText("Please select a category");
            keywordArea.setEnabled(false);
            keywordArea.setText("");
        }
    }

    private void addCategory() {
        String name = JOptionPane.showInputDialog(this.getContentPane(), "Enter category name:");
        if (name != null && !name.trim().isEmpty()) {
            keywordManager.addCategory(name.trim());
            loadCategories();
            categoriesList.setSelectedValue(name.trim(), true);
        }
    }

    private void removeCategory() {
        String selected = categoriesList.getSelectedValue();
        if (selected == null) return;
        
        int confirm = JOptionPane.showConfirmDialog(this.getContentPane(), 
            "Delete category '" + selected + "'?", "Confirm", JOptionPane.YES_NO_OPTION);
        
        if (confirm == JOptionPane.YES_OPTION) {
            keywordManager.removeCategory(selected);
            loadCategories();
        }
    }

    private void saveKeywords() {
        if (currentCategory == null) return;
        
        String text = keywordArea.getText();
        List<String> keywords = Arrays.stream(text.split(";"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        
        keywordManager.updateCategory(currentCategory, keywords);
        JOptionPane.showMessageDialog(this.getContentPane(), "Saved!");
    }
}
