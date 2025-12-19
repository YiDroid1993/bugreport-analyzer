package com.yidroid.buganalyzer.plugin.ui;

import com.yidroid.buganalyzer.core.KeywordManager;
import com.yidroid.buganalyzer.model.FileMetadata;
import com.yidroid.buganalyzer.core.SearchEngine;
import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.JBMenuItem;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class TextPanel extends JPanel {
    
    /** 默认每页显示的行数，用于分页加载防止 OOM */
    private static final int PAGE_SIZE = 2000;

    private JPanel contentPane;
    private JPanel layeredPaneContainer; // Binding for container
    
    // Form Bindings
    private JBTextField searchField;
    private JButton filterTrigger;
    private JCheckBox regexCheckBox;
    private JPanel filterStatusPanel;
    private JLabel filterStatusLabel;
    private JButton clearFilterBtn;
    private JButton prevPageBtn;
    private JLabel pageLabel;
    private JButton nextPageBtn;
    private JButton prevButton;
    private JButton nextButton;
    private JButton searchAllButton;
    private JButton keywordsButton;
    private JLabel statusLabel;
    
    // Components manually added to layeredPane in code
    private final JLayeredPane layeredPane;
    private final JPanel listPanel;
    private JTextArea activeEditor; 
    
    private final JBList<String> list;
    private final DefaultListModel<String> listModel;
    private final KeywordManager keywordManager;
    private Project project;
    
    // --- 分页状态 (Pagination) ---
    private File currentFile;
    private int customPageSize = PAGE_SIZE;
    /** 总行数（用于计算总页数） */
    private long totalLines = 0;
    /** 总页数 */
    private int maxPages = 0;
    /** 当前页码（从 1 开始） */
    private int currentPage = 1;
    
    // --- 搜索状态 (Search State) ---
    /** 当前页内的搜索结果行号列表 */
    private final List<Integer> searchResults = new ArrayList<>();
    /** 当前选中的搜索结果索引 */
    private int currentSearchIndex = -1;
    private boolean isRegex = false;
    /** 当前激活的关键字过滤器列表 */
    private List<String> activeFilters = new ArrayList<>();
    
    // Selection State
    private int anchorIndex = -1;
    private int[] initialSelection = new int[0];
    
    private FileMetadata currentFileMetadata;
    private String projectPath;
    private java.util.function.Consumer<SearchEngine.SearchResult> navigationCallback;
    
    private KeywordFilterPopup keywordFilterPopup;
    private com.intellij.openapi.ui.popup.JBPopup currentFilterPopup;

    public TextPanel() {
        super(new BorderLayout());
        keywordManager = new KeywordManager();
        listModel = new DefaultListModel<>();
        list = new JBList<>(listModel);
        
        // Add form content
        add(contentPane, BorderLayout.CENTER);
        
        // Initialize LayeredPane manually since it needs special layout handling not easily done in form designer without custom component
        layeredPane = new JLayeredPane();
        layeredPane.setLayout(null);
        
        listPanel = new JPanel(new BorderLayout());
        JBScrollPane scrollPane = new JBScrollPane(list);
        listPanel.add(scrollPane, BorderLayout.CENTER);
        
        layeredPane.add(listPanel, JLayeredPane.DEFAULT_LAYER);
        layeredPaneContainer.add(layeredPane, BorderLayout.CENTER);
        
        // Manual Resize Listener for LayeredPane
        layeredPane.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                listPanel.setBounds(0, 0, layeredPane.getWidth(), layeredPane.getHeight());
                listPanel.revalidate();
            }
        });
        
        list.setCellRenderer(new HighlightRenderer());
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        
        CustomMouseAdapter mouseAdapter = new CustomMouseAdapter();
        list.addMouseListener(mouseAdapter);
        list.addMouseMotionListener(mouseAdapter);
        
        // Setup Form Components Logic
        searchField.getEmptyText().setText("Type to search (current page)...");
        
        filterTrigger.addActionListener(e -> showKeywordFilterPopup(filterTrigger));
        
        prevPageBtn.addActionListener(e -> loadPage(currentPage - 1));
        nextPageBtn.addActionListener(e -> loadPage(currentPage + 1));
        
        prevButton.addActionListener(e -> navigateSearch(false));
        nextButton.addActionListener(e -> navigateSearch(true));
        
        searchAllButton.addActionListener(e -> openSearchResultDialog(null));
        keywordsButton.addActionListener(e -> openKeywordDialog());
        
        clearFilterBtn.addActionListener(e -> clearFilters());
        
        // Close editor on scroll
        scrollPane.getVerticalScrollBar().addAdjustmentListener(e -> removeActiveEditor());
        
        // -- Listeners --
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { performSearch(true); }
            public void removeUpdate(DocumentEvent e) { performSearch(true); }
            public void changedUpdate(DocumentEvent e) { performSearch(true); }
        });
        
        regexCheckBox.addActionListener(e -> performSearch(true));
        
        // Zoom listener removed to fix scrolling issue
        // list.addMouseWheelListener(...)
        
        list.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if ((e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) == InputEvent.CTRL_DOWN_MASK && e.getKeyCode() == KeyEvent.VK_A) {
                    list.setSelectionInterval(0, list.getModel().getSize() - 1);
                    e.consume();
                }
            }
        });
    }

    // ... (Public setters and loadFile/Page methods kept same, omitted to save space if not changing) ...
    // BUT we must include them if we replace the whole file or chunk. 
    // I will use partial replacement for the constructor area above if possible, 
    // but here I need to replace CustomMouseAdapter and showInPlaceEditor too.
    // So I will target the bottom half or specific methods.
    
    // WARNING: 'replace_file_content' replaces a CONTIGUOUS block. 
    // I entered the Constructor above. 
    // I will close the constructor and leave the rest for other tools or assume I need to splice.
    // Actually, I'll split this into multiple calls or use 'multi_replace'.
    // I am bound to 'replace_file_content' for single block or 'multi_replace'. 
    // I'll use multi_replace for safety and precision.
    
    // WAIT, I am generating 'ReplacementContent' for the tool call right now.
    // I will CANCEL this large block and use 'multi_replace_file_content'.
    
    // (Self-correction): The tool call is already started. I must finish it or error.
    // I'll use `replace_file_content` for the *constructor* to fix the layout first.
    
    // RESTARTING STRATEGY: 
    // 1. Fix Constructor (Layout).
    // 2. Fix showInPlaceEditor (Bounds/Focus?).
    // 3. Fix CustomMouseAdapter (Context Menu).
    
    // This call will fix the CONSTRUCTOR (Layout).
    
    // ... code for constructor ...
    // I'll just return the constructor part here?
    // No, I need to match existing code.
    
    // Let's use `multi_replace_file_content` instead.


    public void setProject(Project project) { this.project = project; }
    public void setFileMetadata(FileMetadata metadata) { this.currentFileMetadata = metadata; }
    public void setProjectPath(String projectPath) { this.projectPath = projectPath; }
    public void setNavigationCallback(java.util.function.Consumer<SearchEngine.SearchResult> callback) { this.navigationCallback = callback; }
    
    public void loadFile(File file) {
        this.currentFile = file;
        this.currentPage = 1;
        list.setPaintBusy(true);
        
        new Thread(() -> {
            // 1. 计算总行数 (Count Lines)
            try {
                // 使用 Java Stream API 高效计算行数
                totalLines = countLines(file);
                maxPages = (int) Math.ceil((double) totalLines / customPageSize);
                if (maxPages == 0) maxPages = 1;
            } catch (Exception e) {
                totalLines = 0; maxPages = 1;
            }
            
            // 2. 加载第一页
            loadPageInternal(1);
        }).start();
    }
    
    private void loadPage(int page) {
        if (currentFile == null) return;
        if (page < 1 || page > maxPages) return;
        currentPage = page;
        list.setPaintBusy(true);
        new Thread(() -> loadPageInternal(page)).start();
    }

    // 核心分页加载逻辑
    private void loadPageInternal(int page) {
        List<String> lines = new ArrayList<>();
        // ... (RandomAccessFile 块被忽略/优化掉) ...

        // 使用 Java 8 Stream API 的 skip/limit 实现分页读取
        // 这避免了将整个文件加载到内存中，是处理 GB 级日志的关键
        try (java.util.stream.Stream<String> stream = Files.lines(currentFile.toPath(), StandardCharsets.ISO_8859_1)) {
            lines = stream.skip((long)(page - 1) * customPageSize)
                          .limit(customPageSize)
                          .collect(Collectors.toList());
        } catch (IOException e) {
            lines.add("读取文件失败: " + e.getMessage());
        }
        
        final List<String> finalLines = lines;
        SwingUtilities.invokeLater(() -> {
            list.setListData(finalLines.toArray(new String[0]));
            pageLabel.setText(page + "/" + maxPages);
            list.setPaintBusy(false);
            performSearch(true); // 重新执行搜索以高亮当前页结果
            
            if (pendingScrollIndex != -1) {
                if (pendingScrollIndex < list.getModel().getSize()) {
                    list.ensureIndexIsVisible(pendingScrollIndex);
                    list.setSelectedIndex(pendingScrollIndex);
                }
                pendingScrollIndex = -1;
            }
        });
    }

   private long countLines(File file) throws IOException {
       try (java.util.stream.Stream<String> stream = Files.lines(file.toPath(), StandardCharsets.ISO_8859_1)) {
           return stream.count();
       }
   }

    // --- 搜索执行逻辑 (Search Logic) ---
    private void performSearch(boolean forward) {
        String query = searchField.getText();
        boolean hasFilter = !activeFilters.isEmpty();
        
        if (query.isEmpty() && !hasFilter) {
            searchResults.clear();
            currentSearchIndex = -1;
            statusLabel.setText("");
            list.repaint();
            return;
        }
        
        searchResults.clear();
        Pattern pattern = null;
        String lowerQuery = null;
        if (!query.isEmpty()) {
            if (isRegex) {
                try { pattern = Pattern.compile(query, Pattern.CASE_INSENSITIVE); } 
                catch (Exception e) { statusLabel.setText("正则表达式无效"); return; }
            } else { lowerQuery = query.toLowerCase(); }
        }
        
        ListModel<String> model = list.getModel();
        // 遍历当前页的所有行进行匹配
        for (int i = 0; i < model.getSize(); i++) {
            String line = model.getElementAt(i);
            boolean match = true;
            
            // 1. 文本/正则查询匹配
            if (!query.isEmpty()) {
                 if (isRegex) match = pattern.matcher(line).find();
                 else match = line.toLowerCase().contains(lowerQuery);
            }
            
            // 注意：关键字过滤不在此处高亮，而在 list 渲染器或全局搜索中处理
            
            if (match && !query.isEmpty()) searchResults.add(i);
        }
        
        currentSearchIndex = -1;
        if (searchResults.isEmpty()) statusLabel.setText("No matches");
        else navigateSearch(forward);
        list.repaint();
    }
    
    private void navigateSearch(boolean forward) {
        if (searchResults.isEmpty()) return;
        if (forward) {
            currentSearchIndex++;
            if (currentSearchIndex >= searchResults.size()) currentSearchIndex = 0;
        } else {
            currentSearchIndex--;
            if (currentSearchIndex < 0) currentSearchIndex = searchResults.size() - 1;
        }
        int lineIndex = searchResults.get(currentSearchIndex);
        list.ensureIndexIsVisible(lineIndex);
        list.setSelectedIndex(lineIndex);
        statusLabel.setText((currentSearchIndex + 1) + "/" + searchResults.size());
    }

    public void scrollToLine(int line) {
        if (currentFile == null) return;
        
        int page = (line - 1) / customPageSize + 1;
        int indexInPage = (line - 1) % customPageSize;
        
        if (page != currentPage) {
            pendingScrollIndex = indexInPage; // Set pending scroll action
            loadPage(page);
            // loadPage is async. The actual scroll will happen in loadPageInternal's SwingUtilities.invokeLater block
        } else {
            if (indexInPage >= 0 && indexInPage < list.getModel().getSize()) {
                list.ensureIndexIsVisible(indexInPage);
                list.setSelectedIndex(indexInPage);
            }
        }
    }
    
    private int pendingScrollIndex = -1; // Add this field
    
    private void clearFilters() {
        activeFilters.clear();
        filterStatusPanel.setVisible(false);
        if (keywordFilterPopup != null) {
            // Optionally clear check selections in UI?
            // Re-creating popup on next show is simpler.
            keywordFilterPopup = null; 
        }
        performSearch(true);
    }
    
    // --- Filter Popup ---
    private void showKeywordFilterPopup(Component invokeHelper) {
        if (keywordFilterPopup == null) {
            keywordFilterPopup = new KeywordFilterPopup(keywordManager, 
                // onSelectionChanged
                () -> {
                    activeFilters = keywordFilterPopup.getSelectedKeywords();
                    if (!activeFilters.isEmpty()) {
                        filterTrigger.setForeground(JBColor.BLUE);
                        filterTrigger.setToolTipText("Filters Active: " + activeFilters.size());
                    } else {
                        filterTrigger.setForeground(UIManager.getColor("Button.foreground"));
                        filterTrigger.setToolTipText("Filter by Keywords");
                    }
                },
                // onSearchRequested
                () -> {
                    if (currentFilterPopup != null) currentFilterPopup.cancel();
                    openSearchResultDialog(activeFilters);
                }
            );
        }
        
        currentFilterPopup = com.intellij.openapi.ui.popup.JBPopupFactory.getInstance()
             .createComponentPopupBuilder(keywordFilterPopup, null)
             .setTitle("Filter Keywords")
             .setRequestFocus(true)
             .createPopup();
        currentFilterPopup.showUnderneathOf(invokeHelper);
    }
    
    private void openKeywordDialog() {
        KeywordDialog dialog = new KeywordDialog(this, keywordManager);
        dialog.show();
        list.repaint();
    }
    
    // --- 搜索结果对话框 (Open Search Dialog) ---
    private void openSearchResultDialog(List<String> explicitFilters) {
         if (projectPath == null) return;
         
         String query = searchField.getText();
         // 如果 explicitFilters 为空，则搜索全部（使用当前查询 + 当前过滤）
         List<String> filtersToUse = (explicitFilters != null) ? explicitFilters : activeFilters;
         
         if (query.isEmpty() && filtersToUse.isEmpty()) {
             JOptionPane.showMessageDialog(this, "请输入搜索词或选择关键字过滤器。", "需要操作", JOptionPane.WARNING_MESSAGE);
             return;
         }
         
         if (regexCheckBox.isSelected() && !query.isEmpty()) {
             try {
                 Pattern.compile(query);
             } catch (Exception e) {
                 JOptionPane.showMessageDialog(this, "无效的正则表达式:\n" + e.getMessage(), "正则错误", JOptionPane.ERROR_MESSAGE);
                 return;
             }
         }
         
         // 获取 Filter Popup 中的 Match All (AND) 状态
         boolean matchAll = false;
         if (currentFilterPopup != null && keywordFilterPopup != null) {
              matchAll = keywordFilterPopup.isMatchAllSelected();
         }
         
         SearchResultDialog dialog = new SearchResultDialog(this, project, projectPath, currentFileMetadata, query, regexCheckBox.isSelected(), filtersToUse, matchAll, navigationCallback);
         dialog.show();
    }

    // --- 原地编辑器 (In-Place Editor) ---
    /**
     * 在指定行显示悬浮的 JTextArea，用于文本选择和复制。
     * JList 本身不支持文本选择，这是规避方案。
     */
    private void showInPlaceEditor(int index) {
        if (index < 0 || index >= list.getModel().getSize()) return;
        removeActiveEditor();
        
        String text = list.getModel().getElementAt(index);
        
        // 计算单元格在 LayeredPane 中的绝对坐标
        Rectangle cellBounds = list.getCellBounds(index, index);
        Point listLoc = list.getLocationOnScreen();
        Point layeredLoc = layeredPane.getLocationOnScreen();
        
        int x = listLoc.x - layeredLoc.x + cellBounds.x;
        int y = listLoc.y - layeredLoc.y + cellBounds.y;
        
        activeEditor = new JTextArea(text);
        activeEditor.setLineWrap(true);
        activeEditor.setWrapStyleWord(true);
        activeEditor.setFont(list.getFont()); // 保持字体一致
        
        activeEditor.setBackground(list.getBackground());
        activeEditor.setForeground(list.getForeground());
        activeEditor.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(JBColor.BLUE, 1),
            BorderFactory.createEmptyBorder(0, 2, 0, 2)
        ));
        
        // 交互事件监听
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
        
        // 右键菜单
        JBPopupMenu popup = new JBPopupMenu();
        JBMenuItem copyItem = new JBMenuItem("复制");
        copyItem.addActionListener(ev -> activeEditor.copy());
        popup.add(copyItem);
        JBMenuItem searchItem = new JBMenuItem("在代码中搜索");
        searchItem.addActionListener(ev -> searchInCode(activeEditor.getSelectedText()));
        popup.add(searchItem);
        activeEditor.setComponentPopupMenu(popup);
        
        activeEditor.setBounds(x, y, cellBounds.width, Math.max(cellBounds.height, 24)); // 确保最小高度
        
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
    
    // ... (rest omitted, focusing on Renderer next)
    
    // --- 自定义渲染器 (Highlight Renderer) ---
    /**
     * 自定义 ListCellRenderer，用于高亮显示搜索结果。
     * 扩展 JTextArea 以支持多行文本（自动换行）。
     */
    private class HighlightRenderer extends JTextArea implements ListCellRenderer<Object> {
        public HighlightRenderer() {
            setLineWrap(true);
            setWrapStyleWord(true);
            setOpaque(true);
        }
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            setText((String) value);
            setFont(list.getFont());
            if (list.getWidth() > 0) setSize(list.getWidth(), Short.MAX_VALUE);
            
            if (isSelected) {
                setBackground(list.getSelectionBackground());
                setForeground(list.getSelectionForeground());
            } else {
                setBackground(list.getBackground());
                setForeground(list.getForeground());
                
                // 高亮逻辑：仅针对当前搜索结果索引
                if (searchResults.contains(index)) {
                     setBackground(new Color(255, 255, 220)); // 淡黄色高亮
                     setForeground(Color.BLACK); // 强制黑色字体以保证可读性
                }
            }
            return this;
        }
    }

    private class CustomMouseAdapter extends MouseAdapter {
        @Override
        public void mousePressed(MouseEvent e) {
            handleMouseEvent(e);
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            handleMouseEvent(e);
        }
        
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

        private void handleMouseEvent(MouseEvent e) {
            if (e.isPopupTrigger()) {
                showContextMenu(e);
                return;
            }

            if (e.getID() == MouseEvent.MOUSE_PRESSED && SwingUtilities.isLeftMouseButton(e)) {
                int index = list.locationToIndex(e.getPoint());
                
                if (e.getClickCount() == 2 && index != -1) {
                    showInPlaceEditor(index);
                    e.consume();
                    return;
                }
                
                // Custom Selection Start
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
        
        private void showContextMenu(MouseEvent e) {
            int index = list.locationToIndex(e.getPoint());
            if (index == -1) return;
            
            if (!list.isSelectedIndex(index)) {
                list.setSelectedIndex(index);
            }
            
            JBPopupMenu popup = new JBPopupMenu();
            
            JBMenuItem copyItem = new JBMenuItem("Copy");
            copyItem.addActionListener(ev -> {
                List<String> selected = list.getSelectedValuesList();
                if (!selected.isEmpty()) { 
                    CopyPasteManager.getInstance().setContents(new StringSelection(String.join("\n", selected)));
                }
            });
            popup.add(copyItem);
            
            JBMenuItem searchItem = new JBMenuItem("Search in Code");
            searchItem.addActionListener(ev -> {
                String val = list.getSelectedValue();
                if (val != null) searchInCode(val);
            });
            popup.add(searchItem);
            
            JBMenuItem askGeminiItem = new JBMenuItem("Ask Gemini");
            askGeminiItem.addActionListener(ev -> {
               JOptionPane.showMessageDialog(TextPanel.this, "Ask Gemini feature coming soon!", "Gemini", JOptionPane.INFORMATION_MESSAGE);
            });
            popup.add(askGeminiItem);
            
            popup.show(list, e.getX(), e.getY());
        }
    }
    
    private void searchInCode(String query) {
        if (query == null || query.isEmpty()) return;
        if (query.length() > 100) query = query.substring(0, 100);
        FindManager findManager = FindManager.getInstance(project);
        FindModel findModel = findManager.getFindInProjectModel().clone();
        findModel.setStringToFind(query);
        com.intellij.find.findInProject.FindInProjectManager.getInstance(project).findInProject(com.intellij.ide.DataManager.getInstance().getDataContext(this), findModel);
    }
}
