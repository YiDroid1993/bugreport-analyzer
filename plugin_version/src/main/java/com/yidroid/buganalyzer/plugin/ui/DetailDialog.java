package com.yidroid.buganalyzer.plugin.ui;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.openapi.ui.JBMenuItem;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.project.Project;
import com.intellij.find.FindManager;
import com.intellij.find.FindModel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

public class DetailDialog extends DialogWrapper {

    private final String content;
    private final Project project;
    private JBTextArea textArea;

    public DetailDialog(Component parent, Project project, String content) {
        super(parent, true);
        this.project = project;
        this.content = content;
        
        setTitle("Log Detail");
        init();
    }

    @Override
    protected JComponent createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setPreferredSize(new Dimension(600, 400));
        
        textArea = new JBTextArea(content);
        textArea.setEditable(false); // Read-only but selectable
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setCaretPosition(0);
        
        // Context Menu
        JBPopupMenu popup = new JBPopupMenu();
        
        JBMenuItem copyItem = new JBMenuItem("Copy");
        copyItem.addActionListener(e -> textArea.copy());
        popup.add(copyItem);
        
        JBMenuItem searchItem = new JBMenuItem("Search in Code");
        searchItem.addActionListener(e -> searchInCode());
        popup.add(searchItem);
        
        textArea.setComponentPopupMenu(popup);
        
        panel.add(new JBScrollPane(textArea), BorderLayout.CENTER);
        return panel;
    }
    
    private void searchInCode() {
        String query = textArea.getSelectedText();
        if (query == null || query.isEmpty()) {
             // Fallback to whole line if nothing selected? 
             // Or maybe just return. User specifically asked for "character arbitrary selection".
             // If nothing selected, maybe assume cursor word?
             // Let's stick to explicit selection for now.
             return; 
        }
        
        if (project == null) return;
        
        if (query.length() > 100) query = query.substring(0, 100);
        
        FindManager findManager = FindManager.getInstance(project);
        FindModel findModel = findManager.getFindInProjectModel().clone();
        findModel.setStringToFind(query);
        findModel.setCaseSensitive(false);
        findModel.setRegularExpressions(false);
        findModel.setWholeWordsOnly(false);
        
        com.intellij.find.findInProject.FindInProjectManager.getInstance(project).findInProject(com.intellij.ide.DataManager.getInstance().getDataContext(textArea), findModel);
    }
    
    @Override
    protected Action[] createActions() {
        return new Action[]{getOKAction()}; // Only OK button needed (Close)
    }
}
