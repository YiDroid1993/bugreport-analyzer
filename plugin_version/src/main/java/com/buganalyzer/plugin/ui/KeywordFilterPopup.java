package com.buganalyzer.plugin.ui;

import com.buganalyzer.core.KeywordManager;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public class KeywordFilterPopup extends JPanel {
    
    private final KeywordManager keywordManager;
    private final Runnable onSelectionChanged; // Updates "active filters" text
    private final Runnable onSearchRequested;  // Triggers "Search Result Dialog"
    
    // Map Category -> Checkbox
    private final Map<String, JBCheckBox> categoryChecks = new HashMap<>();
    // Map Keyword -> Checkbox
    private final Map<String, JBCheckBox> keywordChecks = new HashMap<>();
    
    private final Map<String, List<String>> categoryToKeywords;

    public KeywordFilterPopup(KeywordManager keywordManager, Runnable onSelectionChanged, Runnable onSearchRequested) {
        super(new BorderLayout());
        this.keywordManager = keywordManager;
        this.onSelectionChanged = onSelectionChanged;
        this.onSearchRequested = onSearchRequested;
        this.categoryToKeywords = keywordManager.getCategorizedKeywords();
        
        initUI();
    }
    
    private void initUI() {
        removeAll();
        setLayout(new BorderLayout());
        
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        
        // Reload data
        keywordChecks.clear();
        categoryChecks.clear();
        Map<String, List<String>> currentData = keywordManager.getCategorizedKeywords();
        
        int row = 0;
        for (Map.Entry<String, List<String>> entry : currentData.entrySet()) {
            String category = entry.getKey();
            List<String> keywords = entry.getValue();
            
            JLabel catLabel = new JLabel(category);
            catLabel.setFont(catLabel.getFont().deriveFont(Font.BOLD));
            catLabel.setBorder(JBUI.Borders.empty(10, 5, 5, 5));
            
            gbc.gridy = row++;
            contentPanel.add(catLabel, gbc);
            
            JPanel gridPanel = new JPanel(new GridLayout(0, 2, 5, 5)); // 2 Columns
            gridPanel.setBorder(JBUI.Borders.emptyLeft(10));
            
            List<JBCheckBox> childChecks = new ArrayList<>();
            for (String kw : keywords) {
                JBCheckBox kwCheck = new JBCheckBox(kw);
                keywordChecks.put(kw, kwCheck);
                childChecks.add(kwCheck);
                gridPanel.add(kwCheck);
                
                kwCheck.addActionListener(e -> {
                    if (onSelectionChanged != null) onSelectionChanged.run();
                });
            }
            
            gbc.gridy = row++;
            contentPanel.add(gridPanel, gbc);
        }
        
        // Filler
        gbc.gridy = row++;
        gbc.weighty = 1.0;
        contentPanel.add(new JPanel(), gbc);
        
        add(new JBScrollPane(contentPanel), BorderLayout.CENTER);
        
        // Bottom Panel
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(JBUI.Borders.empty(5));
        
        JButton manageBtn = new JButton("Manage Keywords");
        manageBtn.addActionListener(e -> {
            new KeywordDialog(this, keywordManager).show();
            // Refresh logic: UI needs to rebuild
            initUI(); 
            revalidate();
            repaint();
        });
        
        JButton searchBtn = new JButton("Search");
        searchBtn.addActionListener(e -> {
            if (onSearchRequested != null) onSearchRequested.run();
        });
        
        bottomPanel.add(manageBtn, BorderLayout.WEST);
        bottomPanel.add(searchBtn, BorderLayout.EAST);
        
        add(bottomPanel, BorderLayout.SOUTH);
        
        setPreferredSize(new Dimension(400, 500));
    }
    
    // ... updateCategoryState unused if we use simple headers? 
    // User asked for "Manage Keywords" layout style (Categories as headers, Items below).
    // I am assuming the previous Checkbox-for-Category was not strictly required if "Manage" style is preferred.
    // Wait, "Filter Keywords" implies toggling. Maybe Category Checkbox is useful to toggle all?
    // I'll stick to Labels for Headers for cleaner look (no gaps) as per "Manage Settings" style usually.
    // If user needs category toggle, they can click individual items.
    
    public List<String> getSelectedKeywords() {
        List<String> selected = new ArrayList<>();
        for (Map.Entry<String, JBCheckBox> entry : keywordChecks.entrySet()) {
            if (entry.getValue().isSelected()) {
                selected.add(entry.getKey());
            }
        }
        return selected;
    }
}
