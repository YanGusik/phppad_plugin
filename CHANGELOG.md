<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# phppad_plugin Changelog

## [Unreleased]
### Added
- Initial scaffold created from [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template)

## [0.0.5] - 2026-03-12
### Fixed
- Claude dialog status not refreshing after "Apply & Restart" — now updates via `invokeLater` after server restart
- Claude dialog showing stale status (e.g. "Running" when server was stopped) — status now re-reads on every window focus gain

## [0.0.4] - 2026-03-10
### Added
- **Compact toolbar** — icon-only buttons (AllIcons), native connection dropdown, edit/delete buttons next to connection, settings in ⚙ popup
- **Adaptive toolbar** — hides connection dropdown when panel is too narrow; buttons never disappear

### Fixed
- Output panel not visible in embedded mode after switching back from scratch mode — `resultContainer` is no longer moved out of the splitter
- Magic comments (`//?`) not working in scratch file mode — `getActiveEditorEx()` now returns the scratch file editor
- `dd()` calls no longer cause "Error" — runner replaces `dd()` with `dump()` so value is captured without killing the script
- Scratch mode output fills the full panel height (splitter proportion set to `0.0`)
- Codebase split into focused files (`EditorMode`, `Toolbar`, `JcefRenderer`, `TreeRenderer`, `MagicComments`, `HttpServer`) for maintainability

## [0.0.1] - 2026-03-08
### Added
- Initial release of **PHPPad — PHP REPL for PhpStorm**
- Interactive PHP scratch pad with full PhpStorm editor support
- Run PHP code on **remote servers via SSH** or inside **Docker containers**
- **Laravel bootstrap** to access models, facades, and helpers
- **Magic comments (`//?`)** to display expression values inline
- **Inline inlay hints** showing results directly in the editor
- **Rich result viewer** with tree structure and type coloring
- **SQL query log** with execution time tracking
- **Exception stack traces** with file and line numbers
- **JCEF HTML renderer** with collapsible structures
- **Swing tree renderer** fallback
- **Code snippets** support
- **Run history** with connection info, duration, and code
- **Multiple connections management** (SSH / Docker)
- **Configurable run shortcut (Ctrl+Enter)**
- **Flexible layout** (vertical / horizontal split)
- **Persistent settings across IDE restarts**

## [0.0.3] - 2026-03-10
### Added
- **Scratch file mode** — edit code in the main PhpStorm editor (`phppad.php` scratch file), output fills the full tool window panel
- **HTTP API server** for Claude integration (configurable host/port, enabled via Claude button)
- **Claude button** — shows server status, Quick Start, API reference, and CLAUDE.md snippet
- **▶ Run in PhpPad** context menu action in the editor (visible only for `phppad.php`)
- **Gutter run button** — green ▶ icon in the gutter of `phppad.php` scratch file
- **Compact toolbar** — icon buttons (AllIcons), native connection dropdown, settings in ⚙ popup
- **Scrollable toolbar** — buttons never disappear when the panel is narrow

### Fixed
- Editor font now matches global PhpStorm font scheme (was hardcoded)
- Commenting out the last code block no longer causes `syntax error: unexpected token "return"`
- Auto-capture of expression values removed — only explicit `dump()` calls produce output
- JSON output in JCEF: Copy button and lazy Tree view per value
- Collapsible result blocks in JCEF renderer
- Renderer (JCEF ↔ Tree) and split orientation can now be switched without restarting PhpStorm
- History "Open" button now correctly replaces editor code
- Snippets and History work correctly in scratch file mode
- `ScratchFileService.findFile` and `FileDocumentManager.getDocument` wrapped in `runReadAction` to prevent threading errors

## [0.0.2] - 2026-03-08
### Fixed
- Fixed editor scrolling issue by wrapping LanguageTextField in JBScrollPane.