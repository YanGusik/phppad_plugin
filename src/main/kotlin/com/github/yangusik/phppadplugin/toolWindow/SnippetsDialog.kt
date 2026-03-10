package com.github.yangusik.phppadplugin.toolWindow

import com.github.yangusik.phppadplugin.services.PhpPadHistoryEntry
import com.github.yangusik.phppadplugin.services.PhpPadSettings
import com.github.yangusik.phppadplugin.services.PhpPadSnippet
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.*
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class SnippetsDialog(
    private val project: Project,
    private val settings: PhpPadSettings,
    private val currentCode: String
) : DialogWrapper(project) {

    var selectedCode: String? = null

    private enum class Tab { SNIPPETS, HISTORY }
    private var currentTab = Tab.SNIPPETS

    private val snippetsTabBtn = makeTabBtn("Snippets", true)
    private val historyTabBtn  = makeTabBtn("History", false)

    private val searchField = SearchTextField()

    private val snippetListModel = DefaultListModel<PhpPadSnippet>()
    private val historyListModel = DefaultListModel<PhpPadHistoryEntry>()
    private val snippetList = JList(snippetListModel)
    private val historyList = JList(historyListModel)
    private val listCard = JPanel(CardLayout())

    private val previewArea = JTextArea().apply {
        isEditable = false
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        background = UIManager.getColor("Editor.background") ?: Color(30, 30, 30)
        foreground = UIManager.getColor("Editor.foreground") ?: Color(212, 212, 212)
        border = JBUI.Borders.empty(8)
        lineWrap = false
    }

    private val metaPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = JBUI.Borders.empty(8, 10, 4, 10)
        isVisible = false
        background = UIManager.getColor("Panel.background")
    }
    private val metaLastRun  = makeMetaLabel()
    private val metaProject  = makeMetaLabel()
    private val metaDuration = makeMetaLabel()

    private val openBtn           = JButton("Open")
    private val deleteBtn         = JButton("Delete")
    private val createSnippetBtn  = JButton("Create Snippet").apply { isVisible = false }

    private val countLabel = JLabel("").apply {
        font = font.deriveFont(11f)
        foreground = UIManager.getColor("Label.disabledForeground")
    }
    private val clearAllBtn = JButton("Clear all").apply {
        isVisible = false
        font = font.deriveFont(11f)
        isBorderPainted = false
        isContentAreaFilled = false
        foreground = UIManager.getColor("Label.disabledForeground")
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    }

    init {
        title = "Snippets & History"
        isModal = true
        init()
        loadSnippets()
        loadHistory()
        switchTab(Tab.SNIPPETS)
        setupListeners()
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    private fun loadSnippets(filter: String = "") {
        snippetListModel.clear()
        settings.snippets.filter {
            filter.isBlank() || it.name.contains(filter, ignoreCase = true) || it.code.contains(filter, ignoreCase = true)
        }.forEach { snippetListModel.addElement(it) }
    }

    private fun loadHistory(filter: String = "") {
        historyListModel.clear()
        settings.history.filter {
            filter.isBlank() || it.connectionName.contains(filter, ignoreCase = true) || it.code.contains(filter, ignoreCase = true)
        }.forEach { historyListModel.addElement(it) }
        countLabel.text = "${historyListModel.size()} entries"
    }

    // ── Listeners ────────────────────────────────────────────────────────────

    private fun setupListeners() {
        snippetsTabBtn.addActionListener { switchTab(Tab.SNIPPETS) }
        historyTabBtn.addActionListener  { switchTab(Tab.HISTORY)  }

        searchField.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent)  { onSearch() }
            override fun removeUpdate(e: DocumentEvent)  { onSearch() }
            override fun changedUpdate(e: DocumentEvent) { onSearch() }
        })

        snippetList.addListSelectionListener { if (!it.valueIsAdjusting) onSnippetSelected() }
        historyList.addListSelectionListener { if (!it.valueIsAdjusting) onHistorySelected() }

        fun openOnEnter(list: JList<*>) = list.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) { if (e.keyCode == KeyEvent.VK_ENTER) doOpen() }
        })
        openOnEnter(snippetList); openOnEnter(historyList)
        snippetList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) { if (e.clickCount == 2) doOpen() }
        })
        historyList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) { if (e.clickCount == 2) doOpen() }
        })

        openBtn.addActionListener          { doOpen() }
        deleteBtn.addActionListener        { doDelete() }
        createSnippetBtn.addActionListener { doCreateSnippet() }
        clearAllBtn.addActionListener      { doClearHistory() }
    }

    private fun onSearch() {
        val q = searchField.text.trim()
        if (currentTab == Tab.SNIPPETS) loadSnippets(q) else loadHistory(q)
    }

    private fun onSnippetSelected() {
        val s = snippetList.selectedValue ?: return
        previewArea.text = s.code
        previewArea.caretPosition = 0
    }

    private fun onHistorySelected() {
        val h = historyList.selectedValue ?: return
        previewArea.text = h.code
        previewArea.caretPosition = 0
        val fmt = SimpleDateFormat("d MMM. yyyy г., HH:mm", Locale.getDefault())
        metaLastRun.text  = "Last Run   ${fmt.format(Date(h.runAt))}"
        metaProject.text  = "Project      ${h.connectionName}"
        metaDuration.text = "Duration    ${"%.2f".format(h.duration)}ms"
        metaPanel.isVisible = true
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private fun doOpen() {
        val code = when (currentTab) {
            Tab.SNIPPETS -> snippetList.selectedValue?.code
            Tab.HISTORY  -> historyList.selectedValue?.code
        }
        if (code != null) { selectedCode = code; close(OK_EXIT_CODE) }
    }

    private fun doDelete() {
        if (currentTab == Tab.SNIPPETS) {
            val s = snippetList.selectedValue ?: return
            val list = settings.snippets
            list.removeIf { it.id == s.id }
            settings.snippets = list
            loadSnippets(searchField.text)
        }
    }

    private fun doCreateSnippet() {
        val code = historyList.selectedValue?.code ?: return
        val name = Messages.showInputDialog(project, "Snippet name:", "Create Snippet", null) ?: return
        if (name.isBlank()) return
        val list = settings.snippets
        list.add(PhpPadSnippet().also { it.name = name.trim(); it.code = code })
        settings.snippets = list
    }

    private fun doClearHistory() {
        val confirm = Messages.showYesNoDialog(project, "Clear all history?", "Confirm", null)
        if (confirm == Messages.YES) {
            settings.history = mutableListOf()
            loadHistory()
            previewArea.text = ""
            metaPanel.isVisible = false
        }
    }

    // ── Tab switching ─────────────────────────────────────────────────────────

    private fun switchTab(tab: Tab) {
        currentTab = tab
        snippetsTabBtn.font = snippetsTabBtn.font.deriveFont(if (tab == Tab.SNIPPETS) Font.BOLD else Font.PLAIN)
        historyTabBtn.font  = historyTabBtn.font.deriveFont(if (tab == Tab.HISTORY)  Font.BOLD else Font.PLAIN)
        (listCard.layout as CardLayout).show(listCard, tab.name)

        previewArea.text = ""
        metaPanel.isVisible = false

        deleteBtn.isVisible         = tab == Tab.SNIPPETS
        createSnippetBtn.isVisible  = tab == Tab.HISTORY
        clearAllBtn.isVisible       = tab == Tab.HISTORY

        if (tab == Tab.SNIPPETS && snippetList.selectedIndex == -1 && snippetListModel.size() > 0)
            snippetList.selectedIndex = 0
        if (tab == Tab.HISTORY && historyList.selectedIndex == -1 && historyListModel.size() > 0)
            historyList.selectedIndex = 0
    }

    // ── Panel builders ────────────────────────────────────────────────────────

    override fun createCenterPanel(): JComponent {
        val tabBar = JPanel(FlowLayout(FlowLayout.LEFT, 2, 0)).apply {
            border = JBUI.Borders.emptyBottom(4)
            add(snippetsTabBtn); add(historyTabBtn)
            add(JButton("+ New Snippet").apply {
                isBorderPainted = false; isContentAreaFilled = false
                foreground = JBColor(Color(100, 160, 220), Color(100, 160, 220))
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                addActionListener {
                    val name = Messages.showInputDialog(project, "Snippet name:", "Save Snippet", null) ?: return@addActionListener
                    if (name.isBlank()) return@addActionListener
                    val list = settings.snippets
                    list.add(PhpPadSnippet().also { it.name = name.trim(); it.code = currentCode })
                    settings.snippets = list
                    switchTab(Tab.SNIPPETS)
                    loadSnippets()
                    snippetList.selectedIndex = 0
                }
            })
        }

        // Left: search + list area
        setupList(snippetList, SnippetCellRenderer())
        setupList(historyList, HistoryCellRenderer())

        val snippetScroll = JBScrollPane(snippetList).apply { border = null }
        val historyScroll = JBScrollPane(historyList).apply { border = null }

        val historyTop = JPanel(BorderLayout(4, 0)).apply {
            border = JBUI.Borders.empty(2, 4)
            add(countLabel, BorderLayout.WEST); add(clearAllBtn, BorderLayout.EAST)
            isOpaque = false
        }
        val historyPanel = JPanel(BorderLayout()).apply {
            add(historyTop, BorderLayout.NORTH); add(historyScroll, BorderLayout.CENTER)
        }

        listCard.add(snippetScroll, Tab.SNIPPETS.name)
        listCard.add(historyPanel, Tab.HISTORY.name)

        val leftPanel = JPanel(BorderLayout(0, 4)).apply {
            border = JBUI.Borders.empty(0, 0, 0, 4)
            add(searchField, BorderLayout.NORTH)
            add(listCard, BorderLayout.CENTER)
            preferredSize = Dimension(290, 0)
        }

        // Right: meta + preview + buttons
        metaPanel.apply {
            add(metaLastRun); add(metaProject); add(metaDuration)
        }

        val previewScroll = JBScrollPane(previewArea).apply {
            border = JBUI.Borders.customLine(UIManager.getColor("Separator.foreground") ?: Color.DARK_GRAY, 1)
        }

        val btnRow = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
            border = JBUI.Borders.emptyTop(6)
            add(openBtn); add(deleteBtn); add(createSnippetBtn)
        }

        val rightPanel = JPanel(BorderLayout(0, 0)).apply {
            add(metaPanel, BorderLayout.NORTH)
            add(previewScroll, BorderLayout.CENTER)
            add(btnRow, BorderLayout.SOUTH)
        }

        val split = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel).apply {
            isContinuousLayout = true; dividerSize = 4; resizeWeight = 0.35
            border = null
        }

        return JPanel(BorderLayout(0, 6)).apply {
            border = JBUI.Borders.empty(8)
            add(tabBar, BorderLayout.NORTH)
            add(split, BorderLayout.CENTER)
            add(JLabel("  Press Enter to open • Double-click • Esc to close").apply {
                font = font.deriveFont(10f)
                foreground = UIManager.getColor("Label.disabledForeground")
            }, BorderLayout.SOUTH)
            preferredSize = Dimension(750, 480)
        }
    }

    override fun createActions(): Array<Action> = emptyArray()

    override fun createSouthPanel(): JComponent? = null

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun makeTabBtn(text: String, selected: Boolean) = JButton(text).apply {
        isBorderPainted = false; isContentAreaFilled = false
        font = font.deriveFont(if (selected) Font.BOLD else Font.PLAIN)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    }

    private fun makeMetaLabel() = JLabel(" ").apply {
        font = font.deriveFont(11f)
        foreground = UIManager.getColor("Label.foreground")
        alignmentX = Component.LEFT_ALIGNMENT
    }

    private fun setupList(list: JList<*>, renderer: ListCellRenderer<*>) {
        @Suppress("UNCHECKED_CAST")
        (list as JList<Any>).cellRenderer = renderer as ListCellRenderer<Any>
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        list.background = UIManager.getColor("List.background")
        list.border = JBUI.Borders.empty(2)
    }

    // ── Cell renderers ────────────────────────────────────────────────────────

    private inner class SnippetCellRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(list: JList<*>, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component {
            val s = value as? PhpPadSnippet ?: return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            val preview = s.code.lines().firstOrNull { it.isNotBlank() }?.take(50) ?: ""
            return JPanel(BorderLayout()).apply {
                border = JBUI.Borders.empty(4, 8)
                background = if (isSelected) UIManager.getColor("List.selectionBackground") else list.background
                add(JLabel("⊞ ${s.name}").apply {
                    font = font.deriveFont(Font.BOLD, 12f)
                    foreground = if (isSelected) UIManager.getColor("List.selectionForeground") else list.foreground
                }, BorderLayout.NORTH)
                add(JLabel(preview).apply {
                    font = font.deriveFont(10f)
                    foreground = UIManager.getColor("Label.disabledForeground")
                }, BorderLayout.CENTER)
            }
        }
    }

    private inner class HistoryCellRenderer : DefaultListCellRenderer() {
        private val fmt = SimpleDateFormat("d MMM. HH:mm", Locale.getDefault())
        override fun getListCellRendererComponent(list: JList<*>, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component {
            val h = value as? PhpPadHistoryEntry ?: return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            val preview = h.code.lines().firstOrNull { it.isNotBlank() }?.take(50) ?: ""
            val bg = if (isSelected) UIManager.getColor("List.selectionBackground") else list.background
            val fg = if (isSelected) UIManager.getColor("List.selectionForeground") else list.foreground
            val dim = UIManager.getColor("Label.disabledForeground")
            return JPanel(BorderLayout()).apply {
                border = JBUI.Borders.empty(4, 8)
                background = bg
                add(JPanel(BorderLayout()).apply {
                    isOpaque = false
                    add(JLabel("⏱ ${h.connectionName}").apply { font = font.deriveFont(Font.BOLD, 11f); foreground = fg }, BorderLayout.WEST)
                    add(JLabel("${"%.2f".format(h.duration)}ms").apply { font = font.deriveFont(10f); foreground = dim }, BorderLayout.EAST)
                }, BorderLayout.NORTH)
                add(JLabel(preview).apply { font = font.deriveFont(10f); foreground = dim }, BorderLayout.CENTER)
                add(JLabel(fmt.format(Date(h.runAt))).apply { font = font.deriveFont(9f); foreground = dim }, BorderLayout.SOUTH)
            }
        }
    }
}
