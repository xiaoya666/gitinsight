# GitInsight

> Not just `git blame`, but `git insight` — a Git change-risk dashboard for IntelliJ IDEs.

[![Marketplace](https://img.shields.io/badge/IntelliJ%20Marketplace-coming%20soon-blue)](https://plugins.jetbrains.com/)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](LICENSE)

## What it does

| Feature | What you see |
|---|---|
| **Enhanced Blame** | Per-line gutter icon with rich tooltip — author, last commit, modify count. Cached locally in SQLite. |
| **Hotspot Heatmap** | A 3px color band (green → red) on the editor gutter and a Top-50 dashboard ToolWindow. Score combines modify count, rollback rate, recency, and author breadth. |
| **Commit Risk Score** | An 8-rule engine that scores every commit (payment / SQL / concurrency / large delete / cross-module / no-tests / CI / hotspot-touch). Surfaces a dialog with explanations + suggestions for MEDIUM / HIGH. |
| **AI Commit Message** | One-click Conventional Commits message from your staged diff. Supports OpenAI, Anthropic Claude, DeepSeek, Ollama, or a free Cloudflare Workers AI fallback. |
| **AI Diff Review** | Right-click a changelist → "🔎 AI Review Diff". Surfaces NPE / BigDecimal / @Transactional / lock / SQL / resource-leak findings. |

## Install

- **From the Marketplace** (recommended once published): search for "GitInsight" in `Preferences > Plugins > Marketplace`.
- **From a release zip**: download the latest from the [releases page](https://github.com/xiaoya666/gitinsight/releases) and use `Preferences > Plugins > ⚙ > Install Plugin from Disk...`.

## Compatibility

- IntelliJ IDEA 2024.2 – 2024.3 (build 242 – 243.\*)
- JVM 21
- Any IDE that bundles `Git4Idea` (IntelliJ IDEA, PhpStorm, PyCharm, etc.)

## Configure

`Preferences > Tools >`:

- **GitInsight: Commit Risk** — per-rule enable/disable for the score engine.
- **GitInsight: AI** — pick a provider, set base URL / model / API key. Keys are stored in the IDE PasswordSafe (OS keychain when available).

The default AI provider is the free Cloudflare Workers AI fallback, so the AI features work out of the box without configuring anything.

## Build from source

```bash
./gradlew buildPlugin       # builds build/distributions/gitInsight-<ver>.zip
./gradlew runIde            # launch a sandbox IDE with the plugin loaded
./gradlew test              # run the unit-test suite
./gradlew verifyPlugin      # JetBrains plugin-verifier against 2024.2 and 2024.3
```

## License

Apache-2.0. See [LICENSE](LICENSE). Core features are open source; the project follows an [Open Core](https://en.wikipedia.org/wiki/Open-core_model) model — AI / team features may move to a separate paid distribution later.
