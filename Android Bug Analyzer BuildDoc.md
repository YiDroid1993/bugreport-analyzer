# Android Bug Analyzer Technical Reference / 技术参考文档

**Version / 版本**: 1.0  
**Platform / 平台**: IntelliJ Platform  

---

## 1. Project Overview / 项目概述

**[EN]**  
**Android Bug Analyzer** is an Android Studio plugin built to solve performance bottlenecks and tooling fragmentation when analyzing large Bug Report log files. It offers a unified analysis experience via a custom high-performance file reading algorithm and integrated search tools.

**[CN]**  
**Android Bug Analyzer** 是一款专为 Android Studio 开发的插件，旨在解决移动开发者在分析大型 Bug Report 日志文件时遇到的性能和工具碎片化问题。它通过自定义的高性能文件读取算法和集成化的搜索工具，提供了一站式的日志分析体验。

---

## 2. Code Structure & Implementation Details / 代码结构与实现细节

Source code is located under `com.yidroid.buganalyzer`, divided into `core` (Logic), `model` (Data), and `plugin.ui` (UI Interaction).  
项目源码位于 `com.yidroid.buganalyzer` 包下，按功能模块划分为 `core` (核心逻辑), `model` (数据模型), 和 `plugin.ui` (界面交互)。

### Package: `com.yidroid.buganalyzer.core`

#### `SearchEngine.java`
**Purpose / 用途**: Provides high-performance, low-memory file searching services. / 提供高性能、低内存占用的文件搜索服务。

**Core Algorithm (searchFile) / 核心算法**:
1.  **Stream Reading / 流式读取**: Uses `BufferedReader` to read files line-by-line, avoiding full file loads into RAM, supporting GB-scale logs on memory-constrained IDEs.
    *   使用 `BufferedReader` 逐行读取文件，避免将整个文件加载到 RAM 中，从而支持只有几百 MB 内存分配的 IDE 处理 GB 级日志。
2.  **Regex Optimization / 正则编译优化**: `Pattern` objects are compiled once outside the loop to avoid redundant compilation overhead.
    *   如果启用了正则搜索，`Pattern` 对象在循环外预编译一次，避免重复编译开销。
3.  **Dual Filter Logic / 双重过滤逻辑**:
    *   **Query**: Checks if line matches the Search Text or Regex. / 首先检查行是否包含查询词。
    *   **Keywords**: Applies keyword filters based on the `matchAllKeywords` flag: / 接着应用关键字过滤器：
        *   `true` (AND): Discards line if **ANY** keyword is missing. / 遍历所有关键字，如果行**缺少任意一个**关键字，则丢弃。
        *   `false` (OR): Keeps line if **ANY** keyword is present. / 遍历所有关键字，如果行**包含任意一个**关键字，则保留。

#### `KeywordManager.java`
**Purpose**: Manages configuration and persistence of user-defined keywords. / 管理用户定义的关键字配置及持久化。

**Logic**: Uses Jackson to serialize keywords/categories to JSON (`keywords.json`). Provides CRUD APIs and handles legacy TXT migration. Data is structured as `Map<String, List<String>>`.  
**实现逻辑**: 使用 Jackson 库将关键字及其分类存储为 JSON 格式。提供了增删改查 API。数据结构为 `Map<String, List<String>>` 实现分类管理。

### Package: `com.yidroid.buganalyzer.model`

#### `ProjectManifest.java`
**Purpose**: Defines the metadata structure for an "Analysis Project". / 定义“分析项目”的元数据结构。
**Fields**: `projectName`, `originalZipPath`, `createdDate`, `files` (List of FileMetadata).

#### `FileMetadata.java`
**Purpose**: Describes attributes of a single analyzed file. / 描述单个被分析文件的属性。
**Logic**: Key field `splitParts` (List<String>) tracks split segments of large files. The UI uses this to present split physical files as a single logical entity.  
**逻辑**: 核心字段 `splitParts` 记录拆分后的子文件名序列，以便 UI 层能够将被拆分的文件呈现为一个逻辑整体。

### Package: `com.yidroid.buganalyzer.plugin.ui`

#### `MainPanel.java`
**Purpose**: Root UI container. / 插件的根 UI 容器。
**Logic**: Uses `CardLayout` to switch between `WELCOME` (Start Screen) and `PROJECT` (Log View). Holds the global `Project` context.

#### `TextPanel.java`
**Purpose**: Core log viewer implementing high-performance pagination. / 核心日志阅读器，实现高性能分页显示。

**Core Algorithms / 核心算法**:
1.  **Pagination (分页加载)**: Uses `Files.lines().skip(offset).limit(limit)` to read only necessary lines for the current page (Default 2000 lines). Avoids OOM.
    *   利用 Java NIO 流只读取当前页所需的行数，避免瞬间加载整个文件。
2.  **Custom Rendering (自定义渲染)**: Extends `JTextArea` as a `ListCellRenderer`. Highlights lines found in `searchResults` by identifying their index during rendering.
    *   扩展 `JTextArea` 实现列表渲染，并根据搜索索引高亮显示匹配行。
3.  **In-Place Editor (原地编辑)**: JList doesn't support text selection. This class calculates the screen bounds of a clicked cell and overlays a transparent `JTextArea` to simulate a selectable text area.
    *   通过计算单元格的屏幕坐标，在顶层动态叠加一个透明的 `JTextArea`，模拟出“双击选中复制”的交互效果。

#### `SearchResultDialog.java`
**Purpose**: Executes global search and displays results. / 执行全局搜索并展示聚合结果。
**Details**:
*   **Threading**: Runs search in a separate `Thread`, periodically checking `isCancelled` to ensure safe termination on dialog close.
*   **Integration**: Passes `keywords` and `matchAll` flags directly to `SearchEngine`.

#### `KeywordFilterPopup.java`
**Purpose**: Floating keyword configuration panel. / 悬浮式关键字过滤配置面板。
**Logic**: Contains the critical "Match All (AND)" checkbox which toggles the filtering logic mode for the entire project search.

---

## 3. Build & Runtime Environment / 构建与运行环境

| Param | Value | Description |
| :--- | :--- | :--- |
| **Language** | Java 17 | Required by IntelliJ Platform 2023.2+ |
| **Build Tool** | Gradle 8.0+ | Manages dependencies and plugin packaging |
| **Framework** | IntelliJ Platform SDK | Target Version: 2023.2.5 |
