package com.yidroid.buganalyzer.plugin.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

public class MainPanel extends JBPanel<MainPanel> {

    private final Project project;
    private final ToolWindow toolWindow;

    private JPanel contentPane; // Bound to .form (CardLayout)

    public MainPanel(Project project, ToolWindow toolWindow) {
        super(new BorderLayout());
        this.project = project;
        this.toolWindow = toolWindow;

        // Note: contentPane is initialized by bytecode instrumentation or $$$setupUI$$$
        if (contentPane == null) {
             // Fallback if not instrumented yet (e.g. during test without build)
             // But we assume build works.
             // Actually, usually we need to call a setup method if not using instrumenter?
             // IntelliJ standard: field is injected.
             // We must add it to THIS panel.
        }
        add(contentPane, BorderLayout.CENTER);

        WelcomePanel welcomePanel = new WelcomePanel(this);
        contentPane.add(welcomePanel, "WELCOME");
        
        CardLayout cl = (CardLayout) contentPane.getLayout();
        cl.show(contentPane, "WELCOME");
    }

    public Project getProject() {
        return project;
    }

    public void showProjectView(com.yidroid.buganalyzer.model.ProjectManifest manifest, String projectPath) {
        CardLayout cl = (CardLayout) contentPane.getLayout();
        ProjectPanel projectPanel = new ProjectPanel(project, manifest, projectPath, () -> cl.show(contentPane, "WELCOME"));
        contentPane.add(projectPanel, "PROJECT");
        cl.show(contentPane, "PROJECT");
    }
}
