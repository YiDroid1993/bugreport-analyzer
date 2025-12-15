package com.buganalyzer.plugin.ui;

import com.buganalyzer.model.FileMetadata;
import com.buganalyzer.model.ProjectManifest;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import javax.swing.*;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.io.File;
import java.util.List;

public class ProjectPanel extends JPanel {

    private final ProjectManifest manifest;
    private final String projectPath;
    private final TextPanel textPanel;
    
    private JPanel contentPane;
    private JButton backButton;
    private JLabel projectLabel;
    private JBList<FileMetadata> fileList;
    private JBList<String> partsList;
    private JPanel textPanelContainer;
    
    // Non-form fields
    private final DefaultListModel<FileMetadata> fileListModel;
    private final DefaultListModel<String> partsListModel;

    public ProjectPanel(com.intellij.openapi.project.Project project, ProjectManifest manifest, String projectPath, Runnable onBack) {
        super(new BorderLayout());
        this.manifest = manifest;
        this.projectPath = projectPath;
        
        // Initialize Models
        fileListModel = new DefaultListModel<>();
        manifest.getFiles().forEach(fileListModel::addElement);
        partsListModel = new DefaultListModel<>();
        
        // Setup Form
        add(contentPane, BorderLayout.CENTER);
        
        // Bind Models
        fileList.setModel(fileListModel);
        partsList.setModel(partsListModel);

        // Setup TextPanel
        this.textPanel = new TextPanel();
        this.textPanel.setProject(project);
        this.textPanel.setProjectPath(projectPath);
        this.textPanel.setNavigationCallback(this::handleNavigation);
        textPanelContainer.add(this.textPanel, BorderLayout.CENTER);

        // Listeners
        backButton.addActionListener(e -> onBack.run());
        projectLabel.setText("Project: " + manifest.getProjectName());

        fileList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof FileMetadata) {
                    setText(((FileMetadata) value).getFileName());
                }
                return this;
            }
        });

        // Listeners
        fileList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                FileMetadata selected = fileList.getSelectedValue();
                if (selected != null) {
                    openFile(selected);
                }
            }
        });
        
        partsList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selectedPart = partsList.getSelectedValue();
                FileMetadata selectedFile = fileList.getSelectedValue();
                if (selectedPart != null && selectedFile != null) {
                    File partFile = new File(projectPath, selectedPart);
                    textPanel.loadFile(partFile);
                }
            }
        });
    }

    private void openFile(FileMetadata metadata) {
        textPanel.setFileMetadata(metadata);
        // Update Parts
        partsListModel.clear();
        List<String> parts = metadata.getSplitParts();
        if (parts != null && !parts.isEmpty()) {
            parts.forEach(partsListModel::addElement);
            
            // Auto-select first part if bugreport
            if (metadata.getType() == FileMetadata.FileType.BUGREPORT) {
                if (partsListModel.getSize() > 0) {
                     partsList.setSelectedIndex(0); // This triggers listener above
                }
            } else {
                 // Regular file
                 File file = new File(projectPath, metadata.getFileName());
                 textPanel.loadFile(file);
            }
        } else {
            // No parts, just open file
            File file = new File(projectPath, metadata.getFileName());
            textPanel.loadFile(file);
        }
    }

    private void handleNavigation(com.buganalyzer.core.SearchEngine.SearchResult result) {
        String filename = result.filePath; // This is strictly the filename in SearchEngine logic
        
        // Find metadata matching this filename OR look through split parts
        FileMetadata found = null;
        String foundPart = null;
        
        for (FileMetadata fm : manifest.getFiles()) {
            if (fm.getFileName().equals(filename)) {
                found = fm;
                break;
            }
            if (fm.getSplitParts() != null) {
                for (String part : fm.getSplitParts()) {
                    if (part.equals(filename)) {
                        found = fm;
                        foundPart = part;
                        break;
                    }
                }
            }
            if (found != null) break;
        }
        
        if (found != null) {
            final FileMetadata targetMeta = found;
            final String targetPart = foundPart;
            
            SwingUtilities.invokeLater(() -> {
                // Select file in list
                fileList.setSelectedValue(targetMeta, true);
                
                // If it was a part, select the part
                if (targetPart != null) {
                     // Wait for parts list to populate via listeners? 
                     // openFile call above (triggered by setSelectedValue) will populate parts.
                     // But we need to wait for that chain reaction.
                     // Using InvokeLater again might push it to end of queue.
                     SwingUtilities.invokeLater(() -> {
                         partsList.setSelectedValue(targetPart, true);
                         SwingUtilities.invokeLater(() -> {
                             textPanel.scrollToLine(result.lineNumber);
                         });
                     });
                } else {
                    SwingUtilities.invokeLater(() -> {
                        textPanel.scrollToLine(result.lineNumber);
                    });
                }
            });
        }
    }
}
