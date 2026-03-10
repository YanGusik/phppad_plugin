# PHPPad — PHP REPL for PhpStorm

![Build](https://github.com/YanGusik/phppad_plugin/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)

<!-- Plugin description -->
## PHPPad — PHP REPL for PhpStorm

A Tinkerwell-like interactive PHP scratch pad that runs code on your **remote server via SSH** or inside a **Docker container** — right inside PhpStorm, without leaving the IDE.

### ✨ Features

#### Editor & Execution
- **Full PHP editor** with syntax highlighting, autocomplete, and all PhpStorm intelligence
- **Scratch file mode** — edit code in the main PhpStorm editor (`phppad.php`), output fills the full tool window
- **SSH & Docker connections** — connect to any remote server or local Docker container
- **Laravel bootstrap** — automatically bootstraps your Laravel app so you can use models, facades, and helpers directly
- **Magic comments** — add `//?` after any expression to see its value inline in the editor
- **Inline inlay hints** — magic comment values appear right next to the code, non-intrusively
- **Ctrl+Enter** to run from the tool window (configurable in Settings → Keymap → PHPPad: Run Code)
- **▶ Run in PhpPad** — right-click context menu action in the editor when `phppad.php` is open
- **Gutter run button** — green ▶ icon in the gutter of `phppad.php` for one-click execution

#### Output
- **JCEF renderer** (default) — beautiful HTML result panel with collapsible result blocks
- **JSON values** — inline Copy button and lazy Tree view for any JSON string in the output
- **SQL query log** — all database queries captured and shown with execution time
- **Exception trace** — full stack traces with file and line numbers
- **Tree renderer** — lightweight Swing fallback with colored node types
- Switch renderer (JCEF ↔ Tree) and split orientation **without restarting PhpStorm**

#### Workflow
- **Snippets** — save frequently used code snippets and insert them instantly
- **Run history** — browse past runs with connection info, duration, and full code
- **Flexible layout** — toggle between vertical (↕) and horizontal (↔) split
- **Multiple connections** — manage SSH and Docker connections with built-in test connection
- **Persistent settings** — connections, last code, and preferences survive IDE restarts

#### Claude Code / AI Integration
- **HTTP API server** built into the plugin (default: `0.0.0.0:7788`)
- Claude can **read and write the editor code** and **run it** on any configured connection
- Works from **WSL2** — connect via `host.docker.internal:7788`
- **Claude button** in the toolbar — server status, port settings, Quick Start guide, API reference, and ready-to-use `CLAUDE.md` snippet
- REST API endpoints:
  - `GET /connections` — list all configured connections
  - `GET /editor` — get current editor code
  - `POST /editor` — set editor code
  - `POST /run` — run code on a connection, returns full result JSON
  - `GET /status` — server status

### 🚀 Quick Start

1. Open the **PHPPad** tool window (bottom bar)
2. Click **+** → Add SSH or Docker connection
3. Write PHP code in the editor
4. Press **Ctrl+Enter** or click **▶ Run**

### 🤖 Claude Code Quick Start

1. Click the **Claude** button in the PhpPad toolbar
2. Copy the generated `CLAUDE.md` snippet
3. Paste it into your project's `CLAUDE.md`
4. Claude Code can now read/write/run PHP code via the HTTP API

```bash
# Check available connections
curl http://localhost:7788/connections

# Run PHP code
curl -X POST http://localhost:7788/run \
  -H "Content-Type: application/json" \
  -d '{"connection": "my-server", "code": "<?php dump(now());"}'
```

### 🔧 Magic Comments

```php
$users = User::limit(10)->get(); //?
// Inline result shown next to the line: ← array(10)

// Works mid-chain too:
User::limit(10)->get()/*?*/->count()/*?*/->pluck('email');
```

### 🖊️ Scratch File Mode

Click **Scratch** in the toolbar to switch to scratch file mode:
- `phppad.php` opens in the main PhpStorm editor — full IDE features, no embedded editor
- The PhpPad tool window shows only the output panel
- Click the **▶** gutter button or right-click → **▶ Run in PhpPad** to execute
- Click **Scratch ✓** again to switch back to embedded mode

<!-- Plugin description end -->

## Installation

- Using the IDE built-in plugin system:

  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "PHPPad"</kbd> >
  <kbd>Install</kbd>

- Manually:

  Download the [latest release](https://github.com/YanGusik/phppad_plugin/releases/latest) and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

---
Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template
