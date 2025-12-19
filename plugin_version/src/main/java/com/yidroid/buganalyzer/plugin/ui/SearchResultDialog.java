package com.yidroid.buganalyzer.plugin.ui;

import com.yidroid.buganalyzer.core.SearchEngine;
import com.yidroid.buganalyzer.model.FileMetadata;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.JBColor;
import com.intellij.openapi.ui.JBMenuItem;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.find.FindManager;
import com.intellij.find.FindModel;

import com.intellij.openapi.ui.Messages;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.swing.border.EmptyBorder;
import java.util.stream.Collectors;

public class SearchResultDialog extends DialogWrapper {

    private final Project project;
    private final String projectPath;
    /** 当前搜索的具体文件元数据（如果仅搜单文件） */
    private final FileMetadata fileMetadata; 
    private final String query;
    private final boolean isRegex;
    
    private final DefaultListModel<SearchEngine.SearchResult> listModel;
    private final JBList<SearchEngine.SearchResult> list;
    private JPanel contentPane;
    private JPanel layeredContainer;
    private JLabel statusLabel; // 绑定到 Form

    // 类似 TextPanel 的分层面板结构，用于支持悬浮编辑器
    private final JLayeredPane layeredPane;
    private final JPanel listPanel;
    private JTextArea activeEditor;
    
    /** 点击搜索结果后的导航回调 */
    private final java.util.function.Consumer<SearchEngine.SearchResult> onNavigate;
    
    // --- 文本选择状态 ---
    private int anchorIndex = -1;
    private int[] initialSelection = new int[0];

    // --- 关键字过滤参数 ---
    private final List<String> keywords;
    /** 是否必须匹配所有关键字 (AND 逻辑) */
    private final boolean matchAllKeywords;

    public SearchResultDialog(Component parent, Project project, String projectPath, FileMetadata fileMetadata, String query, boolean isRegex, List<String> keywords, boolean matchAllKeywords, java.util.function.Consumer<SearchEngine.SearchResult> onNavigate) {
        super(parent, false);
        this.project = project;
        this.projectPath = projectPath;
        this.fileMetadata = fileMetadata;
        this.query = query;
        this.isRegex = isRegex;
        this.keywords = keywords;
        this.matchAllKeywords = matchAllKeywords;
        this.onNavigate = onNavigate;
        
        setTitle("Search Results: " + (query.isEmpty() ? "[Filters Only]" : query) + (matchAllKeywords ? " (AND)" : ""));
        
        // Manual Init of Lists
        listModel = new DefaultListModel<>();
        list = new JBList<>(listModel);
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        
        // Setup Layered Layout manually (not easily done in form for overlay)
        layeredPane = new JLayeredPane();
        layeredPane.setLayout(null);
        
        listPanel = new JPanel(new BorderLayout());
        JBScrollPane scrollPane = new JBScrollPane(list);
        listPanel.add(scrollPane, BorderLayout.CENTER);
        
        layeredPane.add(listPanel, JLayeredPane.DEFAULT_LAYER);
        
        // Resize Listener
        layeredPane.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                listPanel.setBounds(0, 0, layeredPane.getWidth(), layeredPane.getHeight());
                listPanel.revalidate();
            }
        });
        
        // Scroll Listener to close editor
        scrollPane.getVerticalScrollBar().addAdjustmentListener(e -> removeActiveEditor());

        // Renderer
        list.setCellRenderer(new ListCellRenderer<SearchEngine.SearchResult>() {
            private final JTextArea textArea = new JTextArea();
            {
                textArea.setLineWrap(true);
                textArea.setWrapStyleWord(true);
                textArea.setOpaque(true);
                textArea.setBorder(new EmptyBorder(2, 5, 2, 5));
            }

            @Override
            public Component getListCellRendererComponent(JList<? extends SearchEngine.SearchResult> list, SearchEngine.SearchResult value, int index, boolean isSelected, boolean cellHasFocus) {
                textArea.setText("Line " + value.lineNumber + ": " + value.lineContent.trim());
                textArea.setFont(list.getFont());
                
                if (isSelected) {
                    textArea.setBackground(list.getSelectionBackground());
                    textArea.setForeground(list.getSelectionForeground());
                } else {
                    textArea.setBackground(list.getBackground());
                    textArea.setForeground(list.getForeground());
                }
                
                // Width adjustment for wrapping
                int width = list.getWidth();
                if (width > 0) {
                     textArea.setSize(width, Short.MAX_VALUE);
                }
                
                return textArea;
            }
        });
        
        // Add Advanced Selection Logic (Matching TextPanel)
        CustomMouseAdapter mouseAdapter = new CustomMouseAdapter();
        list.addMouseListener(mouseAdapter);
        list.addMouseMotionListener(mouseAdapter);
        
        init();
        
        performSearch();
    }
    
    private String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    @Override
    protected JComponent createCenterPanel() {
        if (layeredContainer != null) {
            layeredContainer.add(layeredPane, BorderLayout.CENTER);
        }
        // Force size to prevent minimization issue
        contentPane.setPreferredSize(new Dimension(1000, 700));
        return contentPane;
    }

    // --- In-Place Editor for Search Results ---
    private void showInPlaceEditor(int index) {
        if (index < 0 || index >= list.getModel().getSize()) return;
        removeActiveEditor();
        
        SearchEngine.SearchResult res = list.getModel().getElementAt(index);
        String text = "Line " + res.lineNumber + ": " + res.lineContent.trim(); // Match renderer text
        
        // Calculate Bounds
        Rectangle cellBounds = list.getCellBounds(index, index);
        Point listLoc = list.getLocationOnScreen();
        Point layeredLoc = layeredPane.getLocationOnScreen();
        
        int x = listLoc.x - layeredLoc.x + cellBounds.x;
        int y = listLoc.y - layeredLoc.y + cellBounds.y;
        
        activeEditor = new JTextArea(text);
        activeEditor.setLineWrap(true);
        activeEditor.setWrapStyleWord(true);
        activeEditor.setFont(list.getFont()); 
        
        activeEditor.setBackground(list.getBackground());
        activeEditor.setForeground(list.getForeground());
        activeEditor.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(JBColor.BLUE, 1),
            BorderFactory.createEmptyBorder(0, 2, 0, 2)
        ));
        
        // Interaction
        activeEditor.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) { removeActiveEditor(); }
        });
        activeEditor.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) removeActiveEditor();
            }
        });
        
        // Context Menu
        JBPopupMenu popup = new JBPopupMenu();
        JBMenuItem copyItem = new JBMenuItem("Copy");
        copyItem.addActionListener(ev -> activeEditor.copy());
        popup.add(copyItem);
        // Maybe "Go to File"?
        JBMenuItem goToItem = new JBMenuItem("Go to File");
        goToItem.addActionListener(ev -> {
            removeActiveEditor();
            if (onNavigate != null) onNavigate.accept(res);
        });
        popup.add(goToItem);
        
        activeEditor.setComponentPopupMenu(popup);
        
        activeEditor.setBounds(x, y, cellBounds.width, Math.max(cellBounds.height, 24)); 
        
        layeredPane.add(activeEditor, JLayeredPane.PALETTE_LAYER);
        layeredPane.revalidate();
        layeredPane.repaint();
        activeEditor.requestFocusInWindow();
    }
    
    private void removeActiveEditor() {
        if (activeEditor != null) {
            layeredPane.remove(activeEditor);
            activeEditor = null;
            layeredPane.revalidate();
            layeredPane.repaint();
            list.requestFocusInWindow();
        }
    }
    
    // --- 后台搜索线程控制 ---
    /** 搜索取消标志位 (volatile 保证线程可见性) */
    private volatile boolean isCancelled = false;
    private Thread searchThread;

    @Override
    protected void dispose() {
        // 关闭对话框时强制中断搜索线程，防止后台资源泄露
        isCancelled = true;
        if (searchThread != null && searchThread.isAlive()) {
            searchThread.interrupt(); 
        }
        super.dispose();
    }

    /**
     * 执行后台搜索任务
     */
    private void performSearch() {
        statusLabel.setText("正在搜索...");
        isCancelled = false;
        
        searchThread = new Thread(() -> {
            List<File> filesToSearch = new ArrayList<>();
            // 线程中断检查点
            if (isCancelled || Thread.currentThread().isInterrupted()) return;
            
            File projectDir = new File(projectPath);
            // ... (文件收集逻辑: 支持单文件、拆分文件零件、或整个文件夹搜索) ...
            if (fileMetadata != null && fileMetadata.getSplitParts() != null && !fileMetadata.getSplitParts().isEmpty()) {
                for (String part : fileMetadata.getSplitParts()) {
                    File f = new File(projectDir, part);
                    if (f.exists()) filesToSearch.add(f);
                }
            } else if (fileMetadata != null) {
                File f = new File(projectDir, fileMetadata.getFileName());
                if (f.exists()) filesToSearch.add(f);
            } else {
                 if (projectDir.isDirectory()) {
                      File[] files = projectDir.listFiles((d, n) -> n.endsWith(".txt"));
                      if (files != null) for(File f : files) filesToSearch.add(f);
                 }
            }
            
            List<SearchEngine.SearchResult> allResults = new ArrayList<>();
            
            for (File f : filesToSearch) {
                 if (isCancelled || Thread.currentThread().isInterrupted()) return; 
                 try {
                     // 调用核心搜索引擎，传入 AND/OR 标志 (matchAllKeywords)
                     List<SearchEngine.SearchResult> res = SearchEngine.searchFile(f, query, isRegex, true, keywords, matchAllKeywords);
                     allResults.addAll(res);
                     // 结果数量软限制，防止撑爆 UI
                     if (allResults.size() > 5000) break;
                 } catch (IllegalArgumentException e) {
                     // 捕获正则错误等
                     SwingUtilities.invokeLater(() -> statusLabel.setText("错误: " + e.getMessage()));
                     return;
                 } catch (Exception e) {
                     return;
                 }
            }
            
            if (isCancelled || Thread.currentThread().isInterrupted()) return;

            // 构建 UI 模型
            DefaultListModel<SearchEngine.SearchResult> newModel = new DefaultListModel<>();
            for (SearchEngine.SearchResult r : allResults) {
                newModel.addElement(r);
            }
            
            // 切换回 EDT (事件分发线程) 更新 UI
            SwingUtilities.invokeLater(() -> {
                if (isCancelled || contentPane == null || !contentPane.isDisplayable()) return;
                list.setModel(newModel); 
                statusLabel.setText("找到 " + allResults.size() + " 个匹配项。" + (allResults.size() >= 5000 ? " (结果已截断)" : ""));
            });
            
        });
        searchThread.start();
    }
    
    // --- Mouse Adapter for Selection and Context Menu ---
    private class CustomMouseAdapter extends MouseAdapter {
        @Override
        public void mousePressed(MouseEvent e) {
            handleMouseEvent(e);
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            handleMouseEvent(e);
        }
        
        private void handleMouseEvent(MouseEvent e) {
            if (e.isPopupTrigger()) {
                showContextMenu(e);
                return;
            }
            
            if (e.getID() == MouseEvent.MOUSE_PRESSED && SwingUtilities.isLeftMouseButton(e)) {
                int index = list.locationToIndex(e.getPoint());
                
                // Double Click -> In-Place Editor (User Request: Match TextPanel)
                if (e.getClickCount() == 2 && index != -1) {
                     showInPlaceEditor(index);
                     e.consume();
                     return;
                }

                anchorIndex = index;
                
                if ((e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) == InputEvent.CTRL_DOWN_MASK) {
                    initialSelection = list.getSelectedIndices();
                } else {
                    initialSelection = new int[0];
                    if (anchorIndex != -1 && !list.isSelectedIndex(anchorIndex)) {
                        list.setSelectedIndex(anchorIndex);
                    }
                }
                
                list.getSelectionModel().setAnchorSelectionIndex(anchorIndex);
                list.getSelectionModel().setLeadSelectionIndex(anchorIndex);
            }
        }

        // ... mouseDragged unchanged ...
        @Override
        public void mouseDragged(MouseEvent e) {
            if (SwingUtilities.isLeftMouseButton(e)) {
                int currentIndex = list.locationToIndex(e.getPoint());
                if (anchorIndex != -1 && currentIndex != -1) {
                    int min = Math.min(anchorIndex, currentIndex);
                    int max = Math.max(anchorIndex, currentIndex);
                    
                    if ((e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) == InputEvent.CTRL_DOWN_MASK) {
                        List<Integer> newSelection = new ArrayList<>();
                        for (int i : initialSelection) newSelection.add(i);
                        
                        for (int i = min; i <= max; i++) {
                            if (newSelection.contains(i)) {
                                newSelection.remove((Integer) i);
                            } else {
                                newSelection.add(i);
                            }
                        }
                        
                        int[] indices = newSelection.stream().mapToInt(Integer::intValue).toArray();
                        list.setSelectedIndices(indices);
                    } else {
                        list.setSelectionInterval(min, max);
                    }
                }
            }
        }
        
        private void showContextMenu(MouseEvent e) {
            JBPopupMenu popupMenu = new JBPopupMenu();
            
            JBMenuItem copyItem = new JBMenuItem("Copy");
            copyItem.addActionListener(ev -> copySelection());
            popupMenu.add(copyItem);
            
            if (list.getSelectedIndices().length == 1) {
                JBMenuItem searchInCodeItem = new JBMenuItem("Search in Code");
                searchInCodeItem.addActionListener(ev -> searchInCode());
                popupMenu.add(searchInCodeItem);
                
                JBMenuItem goToItem = new JBMenuItem("Go to File");
                goToItem.addActionListener(ev -> {
                    SearchEngine.SearchResult res = listModel.get(list.getSelectedIndex());
                    if (onNavigate != null) onNavigate.accept(res);
                });
                popupMenu.add(goToItem);
            }
            
            popupMenu.show(list, e.getX(), e.getY());
        }
    }

    private void copySelection() {
        List<SearchEngine.SearchResult> selected = list.getSelectedValuesList();
        if (!selected.isEmpty()) {
            String text = selected.stream().map(r -> r.lineContent).collect(Collectors.joining("\n"));
            CopyPasteManager.getInstance().setContents(new StringSelection(text));
        }
    }
    
    private void searchInCode() {
        SearchEngine.SearchResult selected = list.getSelectedValue();
        if (selected == null) return;
        String query = selected.lineContent.trim();
        if (query.length() > 100) query = query.substring(0, 100);
        
        FindManager findManager = FindManager.getInstance(project);
        FindModel findModel = findManager.getFindInProjectModel().clone();
        findModel.setStringToFind(query);
        findModel.setCaseSensitive(false);
        findModel.setRegularExpressions(false);
        findModel.setWholeWordsOnly(false);
        
        com.intellij.find.findInProject.FindInProjectManager.getInstance(project).findInProject(com.intellij.ide.DataManager.getInstance().getDataContext(this.getContentPane()), findModel);
    }
}
