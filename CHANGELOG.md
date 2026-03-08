<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# phppad_plugin Changelog

## [Unreleased]
### Added
- Initial scaffold created from [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template)

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

## [0.0.2] - 2026-03-08
### Fixed
- Fixed editor scrolling issue by wrapping LanguageTextField in JBScrollPane.