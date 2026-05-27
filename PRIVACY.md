# GitInsight Privacy Policy

Last updated: 2026-05-27

## TL;DR

- **Your source code never leaves your machine unless you explicitly enable an AI feature.**
- **Telemetry is disabled by default.** When you turn it on, only anonymous exception stacks + IDE/plugin version + a random install ID are sent. No code, no file paths, no usernames, no git URLs.
- **API keys are stored in the IDE's built-in PasswordSafe** (OS keychain), never in plain config files.

GitInsight ("the plugin") is an IntelliJ-platform plugin that analyzes your local Git history to surface change-risk insights. The plugin is published by the maintainer at [github.com/xiaoya666/gitinsight](https://github.com/xiaoya666/gitinsight) and is licensed under Apache-2.0.

## 1. What runs locally (no network)

The following features are **100% local** — they read your local Git repository and SQLite cache only, with zero outbound network calls:

| Feature | Data source |
|---|---|
| Enhanced Blame (line tooltip with incident badge) | local `git blame` via Git4Idea |
| Hotspot Heatmap (gutter + dashboard) | local `git log` history scan, cached in your project's SQLite |
| Commit Risk Score (8-rule engine + YAML overrides) | staged diff + local `.gitinsight/risk.yml` |
| Incident Commit detection (revert / hotfix / rollback) | local commit messages, regex classification |
| Activity Dashboard (last-30-day personal commit stats) | local `git log` |

No data from these features ever leaves your machine.

## 2. Optional network calls (you control them)

### 2.1 AI Commit Message & AI Diff Review

These features are **off by default**. They only fire when you (a) configure a provider in **Settings ▸ Tools ▸ GitInsight: AI** and (b) explicitly invoke the action ("✨ AI Commit Message" / "🔎 AI Review Diff").

| Provider | What is sent | Where it goes |
|---|---|---|
| OpenAI | the staged Git diff (truncated) + recent commit messages | `api.openai.com` (your account, your key) |
| Anthropic Claude | same as above | `api.anthropic.com` (your account, your key) |
| DeepSeek | same as above | `api.deepseek.com` (your account, your key) |
| Ollama | same as above | the local URL you configured (default `http://localhost:11434`) |
| Cloudflare Workers AI (free fallback) | same as above | `api.cloudflare.com` (your Cloudflare account, your key) |

- **What "the diff" contains**: the source-code text of your staged changes plus a few neighboring lines, capped at ~30 files / N tokens. This is the same payload any AI assistant needs to do its job.
- **No telemetry or analytics on top of these calls.** The plugin makes one direct HTTPS request to the provider and shows you the response.
- **No proxy / no gateway in this version.** Requests go directly from your IDE to the provider; the maintainer never sees them.
- **You can disable AI entirely** by leaving the provider set to "Disabled" in settings (default).

### 2.2 Anonymous crash telemetry

- **Default: OFF.** No telemetry is sent unless you opt in via **Settings ▸ Tools ▸ GitInsight: Telemetry**.
- **Endpoint**: the URL you configure in the same settings panel (intended to be a self-hosted Cloudflare Worker run by the maintainer or, for enterprise users, your own).
- **What is sent** (full schema; see `TelemetryService.kt`):

  ```json
  {
    "installId":     "<random UUID generated locally on first run>",
    "kind":          "exception | ping",
    "pluginVersion": "1.0.x",
    "ideBuild":      "IC-242.20224.91",
    "os":            "Mac OS X",
    "message":       "<exception class name or hint, ≤500 chars>",
    "stack":         "<truncated stack trace, ≤4096 chars>"
  }
  ```
- **What is NEVER sent**: source code content, file paths, repository paths, repository URLs, branch / commit hashes, user name, email, license info, or any IDE-side identifying data.
- **Stack-trace contents**: stack traces contain class and method names from the plugin's own code and the IDE platform. They do **not** include user code or local file paths.
- **Retention**: at the maintainer's discretion — the public Cloudflare Worker referenced in the default endpoint will purge events older than 90 days.
- **You can revoke consent at any time** by toggling the setting off; no further events will be sent.

## 3. API keys & secrets

- All provider API keys are stored via IntelliJ's `PasswordSafe`, which delegates to your operating system's keychain (macOS Keychain, Windows Credential Manager, KWallet/Secret Service on Linux).
- API keys are **never** written to project files, the IDE's XML config, or telemetry payloads.
- Uninstalling the plugin leaves keys in the keychain; you can clear them manually via your OS keychain tool.

## 4. Local data storage

- Hotspot / Blame / Incident caches live in a SQLite database under the IDE's per-project system directory.
- The cache contains only what is already on your machine (commit IDs, file paths, modify counts, classifier labels). It is not transmitted anywhere.
- You can delete the cache by removing the `gitinsight` directory under `<project-cache>/system/` or by invoking **File ▸ Invalidate Caches**.

## 5. Third parties

GitInsight does not embed any third-party analytics, advertising, or tracking SDKs. The only outbound calls the plugin can make are:

1. The AI providers you configure (§2.1)
2. The telemetry endpoint you opted into (§2.2)
3. The IntelliJ Marketplace update check (handled by the IDE, not by GitInsight)

## 6. Children & jurisdiction

GitInsight is a developer tool not directed at children under 13. The plugin is published from China; the maintainer respects GDPR / CCPA principles: minimum-necessary collection, opt-in only, full revocation rights.

## 7. Changes to this policy

Material changes will be announced in the plugin's `change-notes` and reflected with a new "Last updated" date at the top of this document. Continued use of the plugin after a change constitutes acceptance.

## 8. Contact

Privacy questions or data-deletion requests: open an issue at [github.com/xiaoya666/gitinsight/issues](https://github.com/xiaoya666/gitinsight/issues).
