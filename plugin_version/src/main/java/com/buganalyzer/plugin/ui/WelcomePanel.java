package com.buganalyzer.plugin.ui;

import com.buganalyzer.core.RecentProjectsManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBLabel;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;

public class WelcomePanel extends JPanel {

    private final MainPanel mainPanel;
    private final RecentProjectsManager recentProjectsManager;
    private JPanel contentPane;
    private JButton openButton;
    private JBList<RecentProjectsManager.RecentProject> projectList;
    private DefaultListModel<RecentProjectsManager.RecentProject> listModel;

    public WelcomePanel(MainPanel mainPanel) {
        super(new BorderLayout());
        this.mainPanel = mainPanel;
        this.recentProjectsManager = new RecentProjectsManager();
        this.listModel = new DefaultListModel<>();

        // Add the bound content pane
        add(contentPane, BorderLayout.CENTER);

        // Bind Listeners
        openButton.addActionListener(e -> openProject());

        // Configure List
        projectList.setModel(listModel);
        projectList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof RecentProjectsManager.RecentProject) {
                    RecentProjectsManager.RecentProject p = (RecentProjectsManager.RecentProject) value;
                    setText(p.getName() + " (" + p.getPath() + ")");
                    setToolTipText(p.getPath());
                }
                return this;
            }
        });

        projectList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    RecentProjectsManager.RecentProject selected = projectList.getSelectedValue();
                    if (selected != null) {
                        loadProject(selected.getPath());
                    }
                }
                if (SwingUtilities.isRightMouseButton(e)) {
                    int index = projectList.locationToIndex(e.getPoint());
                    if (index != -1) {
                         projectList.setSelectedIndex(index);
                         RecentProjectsManager.RecentProject selected = projectList.getSelectedValue();
                         if (selected != null) {
                             JPopupMenu menu = new JPopupMenu();
                             JMenuItem deleteItem = new JMenuItem("Delete Project");
                             deleteItem.addActionListener(ev -> {
                                 recentProjectsManager.removeProject(selected.getPath());
                                 refreshList();
                             });
                             menu.add(deleteItem);
                             menu.show(projectList, e.getX(), e.getY());
                         }
                    }
                }
            }
        });

        refreshList();
    }

    private void refreshList() {
        listModel.clear();
        for (RecentProjectsManager.RecentProject p : recentProjectsManager.getRecentProjects()) {
            listModel.addElement(p);
        }
    }

    private void openProject() {
        FileChooserDescriptor descriptor = new FileChooserDescriptor(true, true, true, true, false, false);
        descriptor.setTitle("Open Bug Report");
        descriptor.setDescription("Select a bug report zip file or project JSON");
        
        VirtualFile file = FileChooser.chooseFile(descriptor, mainPanel.getProject(), null);
        if (file != null) {
            File selectedFile = new File(file.getPath());
            if (selectedFile.getName().toLowerCase().endsWith(".zip")) {
                processZipFile(selectedFile);
            } else if (selectedFile.getName().toLowerCase().endsWith(".json")) {
                loadProjectFromFile(selectedFile);
            } else {
                // Assume directory or other file, try to find json? 
                // For now, simpler to just support zip or json.
                JOptionPane.showMessageDialog(this, "Please select a .zip file or a project .json file.");
            }
        }
    }

    private void processZipFile(File zipFile) {
        // Show progress (simple modal dialog or similar)
        // In IntelliJ plugin, we should use Task.Backgroundable, but for simplicity we'll do a simple thread with SwingUtilities for now.
        // A proper ProgressManager usage is better but let's stick to simple Swing first to emulate logic.
        
        JDialog progressDialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Processing", true);
        progressDialog.setLayout(new BorderLayout());
        JLabel statusLabel = new JLabel("Extracting " + zipFile.getName() + "...", SwingConstants.CENTER);
        progressDialog.add(statusLabel, BorderLayout.CENTER);
        progressDialog.setSize(300, 100);
        progressDialog.setLocationRelativeTo(this);
        
        new Thread(() -> {
            try {
                com.buganalyzer.core.ZipExtractor extractor = new com.buganalyzer.core.ZipExtractor();
                // Ensure ZipExtractor is static or instantiated. The standalone used static methods!
                // Checking ZipExtractor code... it has static `extractProject`.
                com.buganalyzer.model.ProjectManifest manifest = com.buganalyzer.core.ZipExtractor.extractProject(zipFile);
                
                // Save JSON
                File projectDir = new File(new File(manifest.getOriginalZipPath()).getParent(), manifest.getProjectName());
                com.buganalyzer.core.ProjectManager.saveProject(manifest, projectDir);
                
                SwingUtilities.invokeLater(() -> {
                    progressDialog.dispose();
                    File jsonFile = new File(projectDir, manifest.getProjectName() + ".json");
                    recentProjectsManager.addProject(manifest.getProjectName(), jsonFile.getAbsolutePath());
                    refreshList();
                    loadProjectInternal(manifest, projectDir.getAbsolutePath());
                });
                
            } catch (Exception e) {
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    progressDialog.dispose();
                    JOptionPane.showMessageDialog(this, "Error processing zip: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
        
        progressDialog.setVisible(true);
    }

    private void loadProjectFromFile(File jsonFile) {
        try {
            com.buganalyzer.model.ProjectManifest manifest = com.buganalyzer.core.ProjectManager.loadProject(jsonFile);
            
            // Logic to find project dir
            File projectDir;
            if (jsonFile.getParentFile().getName().equals(manifest.getProjectName())) {
                projectDir = jsonFile.getParentFile();
            } else {
                projectDir = new File(jsonFile.getParent(), manifest.getProjectName());
            }
            
            recentProjectsManager.addProject(manifest.getProjectName(), jsonFile.getAbsolutePath());
            refreshList();
            loadProjectInternal(manifest, projectDir.getAbsolutePath());
            
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error loading project: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadProject(String path) {
        File file = new File(path);
        if (file.exists()) {
             if (file.isDirectory()) {
                 // Try to find json inside
                 // This is legacy fallback or if user picked dir.
                 // We should look for *.json with same name?
                 // For now, let's assume path points to the JSON in the recent list.
                 // If recent list has dirs (legacy), we might fail.
                 // Let's assume JSON.
             }
             loadProjectFromFile(file);
        } else {
            JOptionPane.showMessageDialog(this, "Project file not found: " + path);
            recentProjectsManager.removeProject(path);
            refreshList();
        }
    }

    private void loadProjectInternal(com.buganalyzer.model.ProjectManifest manifest, String projectPath) {
        mainPanel.showProjectView(manifest, projectPath);
    }

    // Constructor Update for Context Menu
    //{
        // Add this block inside constructor after projectList init
        // BUT replace_file_content is tricky with constructor injection.
        // We will do a separate edit for adding mouse listener if needed or include it here if we replace constructor?
        // Wait, replace_file_content replaces a block. I can replace the openProject and loadProject methods.
        // I also need to update the constructor to add the popup menu.
}
