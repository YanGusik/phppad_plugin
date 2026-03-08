# phppad_plugin

![Build](https://github.com/YanGusik/phppad_plugin/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)

## Template ToDo list
- [x] Create a new [IntelliJ Platform Plugin Template][template] project.
- [ ] Get familiar with the [template documentation][template].
- [ ] Adjust the [pluginGroup](./gradle.properties) and [pluginName](./gradle.properties), as well as the [id](./src/main/resources/META-INF/plugin.xml) and [sources package](./src/main/kotlin).
- [ ] Adjust the plugin description in `README` (see [Tips][docs:plugin-description])
- [ ] Review the [Legal Agreements](https://plugins.jetbrains.com/docs/marketplace/legal-agreements.html?from=IJPluginTemplate).
- [ ] [Publish a plugin manually](https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html?from=IJPluginTemplate) for the first time.
- [ ] Set the `MARKETPLACE_ID` in the above README badges. You can obtain it once the plugin is published to JetBrains Marketplace.
- [ ] Set the [Plugin Signing](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html?from=IJPluginTemplate) related [secrets](https://github.com/JetBrains/intellij-platform-plugin-template#environment-variables).
- [ ] Set the [Deployment Token](https://plugins.jetbrains.com/docs/marketplace/plugin-upload.html?from=IJPluginTemplate).
- [ ] Click the <kbd>Watch</kbd> button on the top of the [IntelliJ Platform Plugin Template][template] to be notified about releases containing new features and fixes.
- [ ] Configure the [CODECOV_TOKEN](https://docs.codecov.com/docs/quick-start) secret for automated test coverage reports on PRs

<!-- Plugin description -->
## PHPPad — PHP REPL for PhpStorm

A Tinkerwell-like interactive PHP scratch pad that runs code on your **remote server via SSH** or inside a **Docker container** — right inside PhpStorm, without leaving the IDE.

### ✨ Features

- **Full PHP editor** with syntax highlighting, autocomplete, and all PhpStorm intelligence
- **SSH & Docker connections** — connect to any remote server or local Docker container
- **Laravel bootstrap** — automatically bootstraps your Laravel app so you can use models, facades, and helpers directly
- **Magic comments** — add `//?` after any expression to see its value inline in the editor
- **Inline inlay hints** — magic comment values appear right next to the code, non-intrusively
- **Return values** — all expression results displayed in a rich tree view with type coloring
- **SQL query log** — all database queries captured and shown with execution time, collapsed by default
- **Exception trace** — full stack traces with file and line numbers
- **JCEF renderer** (default) — beautiful HTML result panel with collapsible Tree/Table toggle for collections
- **Tree renderer** — lightweight Swing fallback with colored node types
- **Snippets** — save frequently used code snippets and insert them instantly
- **Run history** — browse past runs with connection info, duration, and full code; create snippets from history
- **Ctrl+Enter** to run (configurable in Settings → Keymap → PHPPad: Run Code)
- **Flexible layout** — toggle between vertical (↕) and horizontal (↔) split
- **Multiple connections** — manage SSH and Docker connections with built-in test connection
- **Persistent settings** — connections, last code, and preferences survive IDE restarts

### 🚀 Quick Start

1. Open the **PHPPad** tool window (bottom bar)
2. Click **+** → Add SSH or Docker connection
3. Write PHP code in the editor
4. Press **Ctrl+Enter** or click **▶ Run**

### 🔧 Magic Comments

```php
$users = User::limit(10)->get(); //?
// Inline result shown next to the line: ← array(10)

// Works mid-chain too:
User::limit(10)->get()/*?*/->count()/*?*/->pluck('email');
```
<!-- Plugin description end -->

## Installation

- Using the IDE built-in plugin system:

  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "phppad_plugin"</kbd> >
  <kbd>Install</kbd>

- Using JetBrains Marketplace:

  Go to [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID) and install it by clicking the <kbd>Install to ...</kbd> button in case your IDE is running.

  You can also download the [latest release](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID/versions) from JetBrains Marketplace and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

- Manually:

  Download the [latest release](https://github.com/YanGusik/phppad_plugin/releases/latest) and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>


---
Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template
[docs:plugin-description]: https://plugins.jetbrains.com/docs/intellij/plugin-user-experience.html#plugin-description-and-presentation
