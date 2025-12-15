package com.buganalyzer.plugin.ui;

import com.buganalyzer.core.AISiteManager;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.openapi.ui.DialogWrapper;
import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import org.jetbrains.annotations.Nullable;

public class AIConsultationDialog extends DialogWrapper {

    private final String context;
    private final AISiteManager siteManager;

    public AIConsultationDialog(String context) {
        super(true); // use current window as parent
        this.context = context;
        this.siteManager = new AISiteManager();
        setTitle("Consult AI");
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        
        // Left: Browser
        JPanel leftPanel = new JPanel(new BorderLayout());
        
        // Top Bar: Selector
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JComboBox<AISiteManager.AISite> siteSelector = new JComboBox<>();
        for (AISiteManager.AISite site : siteManager.getSites()) {
            siteSelector.addItem(site);
        }
        
        JBCefBrowser browser = new JBCefBrowser();
        if (siteSelector.getItemCount() > 0) {
            browser.loadURL(siteSelector.getItemAt(0).getUrl());
        }
        
        siteSelector.addActionListener(e -> {
            AISiteManager.AISite selected = (AISiteManager.AISite) siteSelector.getSelectedItem();
            if (selected != null) {
                browser.loadURL(selected.getUrl());
            }
        });
        
        topBar.add(new JLabel("AI Model:"));
        topBar.add(siteSelector);
        leftPanel.add(topBar, BorderLayout.NORTH);
        leftPanel.add(browser.getComponent(), BorderLayout.CENTER);
        
        // Right: Context
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.add(new JLabel("Selected Context:"), BorderLayout.NORTH);
        
        DefaultListModel<String> listModel = new DefaultListModel<>();
        if (context != null && !context.isEmpty()) {
            Arrays.stream(context.split("\n")).forEach(listModel::addElement);
        }
        JBList<String> contextList = new JBList<>(listModel);
        rightPanel.add(new JBScrollPane(contextList), BorderLayout.CENTER);
        
        splitPane.setLeftComponent(leftPanel);
        splitPane.setRightComponent(rightPanel);
        splitPane.setDividerLocation(600);
        splitPane.setPreferredSize(new Dimension(1000, 700));

        return splitPane;
    }
}
