# GitInsight — IntelliJ IDEA 智能 Git 分析插件
## 完整需求 & 执行计划

> 版本：v0.3 草案
> 编写日期：2026-05-17（v0.1 → v0.3 同日完成所有 TODO 决策）
> 参考文档：`doc/Git Insight需求计划.md`
> 状态：所有阻塞性 TODO 已决，**可立即进入 Sprint 0**

---

## 决策记录（Decision Log）

| 日期 | 决策点 | 结论 | 备注 |
|---|---|---|---|
| 2026-05-17 | **T-d** 清理 JavaFX 脚手架 | 同意删除重建 | Sprint 0 第一件事 |
| 2026-05-17 | **P1** 插件名 | **GitInsight**（无空格，与 Marketplace 现有 "Git Insight" ID 29982 拼写区分） | ⚠️ 与 makcbrain 的 "Git Insight" 视觉相近，存在用户混淆风险，上线前再决定是否避让 |
| 2026-05-17 | **P2** 定位 | **个人开发者优先**（PLG 路径） | v0.4-v0.6 先做 AI 功能，团队功能延后 |
| 2026-05-17 | **P3** 开源策略 | **Open Core**：核心 Apache-2.0 开源，AI/企业版闭源 | 类比 Sentry / GitLab / Posthog |
| 2026-05-17 | **T-a** 最低 IDE 版本 | **2024.2**（build 242） | 覆盖更广，2024.3 新 API 非 MVP 必需 |
| 2026-05-17 | **T-b** Android Studio | v0.x 不支持，v1.0 评估 | AS 平台版本滞后，兼容成本高 |
| 2026-05-17 | **T-c** 本地缓存 | **SQLite + SQLDelight** | 类型安全、schema 迁移友好 |
| 2026-05-17 | **C-a** 域名 | 暂缓，用 `gitinsight.pages.dev` 占位 | |
| 2026-05-17 | **C-b** Workers AI 兜底 | **启用**，模型 `@cf/qwen/qwen1.5-7b-chat-awq` | 免费用户无需自带 Key |
| 2026-05-17 | **C-c** AI Gateway 路由 | **Pro 强制走 / Free 可选** | Free 默认直连保护隐私 |
| 2026-05-17 | **C-d** 服务端启动时机 | MVP 不搭，现在仅占坑 Pages/Workers 子域 | 正式接入推到 v0.4 |
| 2026-05-17 | **B-a** 三档功能切分 | Free / Pro / Enterprise，见 §11.4 表 | |
| 2026-05-17 | **B-b** 定价 | **Pro ¥199/年 / ¥25/月**；早鸟首年 ¥129；海外 $29/年 | 含国内微信/支付宝通道 |
| 2026-05-17 | **B-c** OSS 维护者免费 | **采纳**，公开 repo + maintainer 身份验证 | |
| 2026-05-17 | **E-a** 测试 | TDD：JUnit5 + MockK + IntelliJ Platform Test Framework | |
| 2026-05-17 | **E-b** CI | **GitHub Actions**：PR=build+test，main=verifier，tag=publish | |
| 2026-05-17 | **E-c** 遥测 | 自建 Cloudflare Worker endpoint，默认关闭，仅崩溃堆栈 | 不上 Sentry |

> 命名最终确认：保留原 **GitInsight** 名称。包名 `com.power.gitinsight`，Gradle root `gitInsight`，Marketplace display name `GitInsight`（无空格）。
>
> Marketplace 重名提醒：plugin id `com.power.gitinsight` 与 makcbrain 的 `Git Insight` (ID 29982) 不同（id 唯一即可上架），但 display name 视觉相近，上线前评估是否调整副标题或加 emoji 区分。

---

## 0. 文档说明

本文档是基于参考稿 `Git Insight需求计划.md` 重新梳理与扩展的**可执行版本**，目标是让一个独立开发者（或 2-3 人小团队）能够**按阶段从 0 到 1 落地**这个插件。

阅读顺序建议：
1. 章节 1-3：定位 / 目标 / 用户场景（产品视角）
2. 章节 4-7：功能模块 / 技术架构 / 数据模型（工程视角）
3. 章节 8-10：里程碑 / 风险 / 商业化（落地视角）
4. 章节 11：TODO 清单（未决项汇总）

---

## 1. 产品定位

**名称**：GitInsight（中文：Git 洞察）
**Slogan**：把 `git blame` 变成 `git insight` —— 不仅知道谁改了，还知道这改动有多危险。
**形态**：IntelliJ Platform 插件（支持 IDEA / Android Studio / PyCharm 等全家桶）
**核心价值**：

| 传统 Git 工具回答 | GitInsight 想回答 |
|---|---|
| 谁改的？ | 谁改的 + 历史可靠度 + 当前是否事故高危区 |
| 改了什么？ | 改了什么 + 这块代码近 30 天热度 / 回滚率 |
| 何时改的？ | 何时改 + 与近期 Bug / 事故的关联 |
| —— | **这次提交风险评分多少？要不要拦截？** |

**一句话定位**：**面向工程师的代码变更风险驾驶舱**。

---

## 2. 目标用户与场景

### 2.1 用户画像

| 角色 | 痛点 | 用插件做什么 |
|---|---|---|
| 后端开发（个人） | 不知道自己改的代码是不是核心高危区 | 编辑时看到"风险提示"，提交前看到 Risk Score |
| Tech Lead | Code Review 看不过来，关键改动容易漏审 | PR 风险榜单 / 高危改动告警 |
| 团队负责人 | 不知道谁是"事故制造机"、哪些模块没人维护 | 团队热力图 / Bus Factor 报表 |
| 新人 | 不熟悉项目，不敢改"老代码" | Hover 看到代码档案：作者 / 修改频次 / 是否曾回滚 |

### 2.2 典型场景

- **场景 A（个人 / MVP 核心）**：开发者在 IDEA 中写代码，鼠标 hover 看到本行最近一次修改、改了几次、是否在事故 commit 中出现过。
- **场景 B（个人 / MVP 核心）**：开发者点 Commit 按钮前，IDE 弹出 Risk Score 与触发规则，可选择继续 / 放弃 / 申请评审。
- **场景 C（专业版）**：AI 生成 Commit Message + AI 对本次 Diff 做风险解读。
- **场景 D（企业版）**：服务端聚合多仓库数据，输出"组织代码健康度仪表盘"。

---

## 3. 范围划分（MVP vs 后续）

### 3.1 MVP（v0.1 – v0.3）—— 必须做

- [x] 项目骨架（IDEA Plugin + Gradle Kotlin DSL）
- [ ] Enhanced Blame（行级悬浮卡片）
- [ ] File Hotspot 热力图（编辑器侧边栏）
- [ ] Commit Risk Score（提交前评分 + 规则引擎）
- [ ] 基础设置面板（规则开关、AI Key 配置）

### 3.2 v0.4 – v0.6（专业版核心）—— 单机即可用

- [ ] AI Commit Message（OpenAI / Claude / DeepSeek / Ollama 任选）
- [ ] AI Diff Review（识别空指针 / BigDecimal / 事务 / SQL 问题）
- [ ] ToolWindow：本地 Dashboard（个人提交统计、风险趋势）
- [ ] 事故 Commit 识别（基于 revert / hotfix 关键字 + 回滚链路）

### 3.3 v0.7+（企业版）—— 需要服务端

- [ ] PR 风险分析（接 GitHub / GitLab Webhook）
- [ ] 团队治理报表（Bus Factor / 高频事故作者 / 模块所有权）
- [ ] 私有化部署包（Docker / Helm）
- [ ] License / SSO

---

## 4. 功能模块详细设计

### 4.1 模块 1：Enhanced Blame（增强版 Blame）

**触发**：编辑器内 hover 在某一行 → 弹出 RichTooltip。

**展示内容**：

```
┌─────────────────────────────────────────────────┐
│ 张三  ·  2 days ago  ·  feat(payment): 支持退款分账 │
│ ─────────────────────────────────────────────── │
│ 本行近 90 天被修改 6 次（高于项目均值 3x）           │
│ 关联事故 commit：abc1234（2026-04-12 回滚）         │
│ 风险标签：[支付] [BigDecimal]                       │
│ ─────────────────────────────────────────────── │
│ [ 查看历史 ]  [ 显示热力图 ]  [ AI 解读 ]            │
└─────────────────────────────────────────────────┘
```

**技术要点**：
- 使用 `com.intellij.openapi.vcs.annotate.FileAnnotation` 拿到 Git Blame 数据
- 缓存维度：`<repoRoot, filePath, rev>` → `BlameSnapshot`，本地 SQLite/Map 持久化
- 频次统计：扫描该行所属"代码块"（基于 PSI Method 范围）的历史 commit
- Hover UI：`com.intellij.openapi.editor.markup.RangeHighlighter` + `LineMarkerProvider`

### 4.2 模块 2：File Hotspot 热力图

**展示**：编辑器左侧 gutter 渲染色带（绿 → 黄 → 红），ToolWindow 内提供"项目级热力图"视图。

**评分公式（v1）**：

```
hotspot = w1 * log(modifyCount)
        + w2 * recencyDecay(lastModifiedAt)   // 越近权重越高
        + w3 * conflictCount
        + w4 * rollbackCount
        + w5 * authorDiversity                // 多人改 → 更危险
```

- 默认权重：`w1=1.0, w2=2.0, w3=1.5, w4=3.0, w5=0.8`（可在设置中调）
- 归一化到 0-100，> 70 标红

### 4.3 模块 3：Commit Risk Score（提交风险评分）

**触发时机**：用户点击 IDEA 的 Commit 按钮 → 在 `CheckinHandler` 中插入分析步骤。

**规则引擎（v1，硬编码 + 可配置 YAML 覆盖）**：

| 规则 | 触发条件 | 默认分值 |
|---|---|---|
| 修改支付 / 金额相关 | 路径 / 类名包含 `pay`,`payment`,`order.*amount`,`bigdecimal` | +30 |
| 修改 SQL / Migration | `*.sql`, `*Mapper.xml`, `migration/*` | +20 |
| 修改并发 / 锁 | 出现 `synchronized`,`ReentrantLock`,`Redisson`,`@Transactional` | +20 |
| 删除大段代码 | 删除行数 > 100 或 > 50% 原文件 | +15 |
| 跨模块修改 | 改动文件分布在 ≥ 3 个顶层包 | +15 |
| 无测试 | 改动包含非测试源文件但 Δ 测试文件 = 0 | +20 |
| 修改 CI / 部署 | `.github/**`,`Dockerfile`,`*.gradle.kts`,`pom.xml` | +10 |
| 触碰高 Hotspot 文件 | 文件 hotspot > 70 | +15 |

**输出**：

```
Risk Score: 82 / 100  (HIGH)
触发规则：
  • 修改支付 (+30)
  • 触碰高 Hotspot 文件 OrderService.kt (+15)
  • 无测试覆盖 (+20)
  • 跨 3 个模块 (+15)
建议：补充单元测试 / 邀请 @张三 评审

[ 继续提交 ]  [ 取消 ]  [ 让 AI 审一下 ]
```

### 4.4 模块 4：AI Commit Message

**输入**：本次 staged diff（截断到 N tokens）+ 最近 5 条 commit 风格样例
**输出**：Conventional Commits 风格的中英文双语建议（用户可选）

**Provider 抽象**：

```kotlin
interface AiProvider {
    suspend fun complete(prompt: String, opts: AiOptions): String
}

// 内置实现
class OpenAiProvider, ClaudeProvider, DeepSeekProvider, OllamaProvider
class CloudflareWorkersAiProvider  // ★ 见 §6 Cloudflare 方案
```

### 4.5 模块 5：AI Diff Review（专业版）

**触发**：右键 changelist → "AI Review This Diff"
**输出**：Inline 评论卡片，按文件 / 按 Hunk 分组

**Java / Kotlin Spring 关注点**（Prompt 内置）：
- 空指针 / `!!`、平台类型
- `BigDecimal` 精度（除法未指定 scale / `==` 比较）
- `@Transactional` 失效（self-invocation、private、rollbackFor 缺失）
- 死锁 / 锁粒度
- SQL：`select *`、缺索引提示、`UPDATE/DELETE` 无 WHERE
- 资源未关闭（`try-with-resources`）

---

## 5. 技术架构

### 5.1 高层结构

```
┌─────────────────────────────────────────────────────┐
│              GitInsight IDEA Plugin                  │
├─────────────────────────────────────────────────────┤
│  UI Layer                                            │
│   ├─ EditorGutter (Hotspot)                          │
│   ├─ LineMarker / Tooltip (Enhanced Blame)           │
│   ├─ ToolWindow (Dashboard)                          │
│   └─ CheckinHandler (Risk Dialog)                    │
├─────────────────────────────────────────────────────┤
│  Domain                                              │
│   ├─ BlameService     (PSI + Git4Idea)               │
│   ├─ HotspotService   (统计 + 衰减算法)               │
│   ├─ RiskEngine       (规则 + YAML)                   │
│   └─ AiOrchestrator   (Provider 路由 + 缓存)          │
├─────────────────────────────────────────────────────┤
│  Infrastructure                                      │
│   ├─ GitAdapter       (git4idea > JGit fallback)     │
│   ├─ Storage          (SQLite via SQLDelight)        │
│   ├─ AiProviders      (OpenAI/Claude/DS/Ollama/CF)   │
│   └─ Telemetry        (匿名埋点，可关闭)              │
└─────────────────────────────────────────────────────┘
```

### 5.2 关键技术决策

| 决策点 | 选择 | 理由 |
|---|---|---|
| 语言 | **Kotlin**（主）+ 少量 Java | IDE 原生支持、协程友好 |
| 构建 | Gradle Kotlin DSL + `intellij-platform-gradle-plugin` 2.x | JetBrains 官方推荐（取代旧 `gradle-intellij-plugin`） |
| 最低 IDE 版本 | 2024.2（build 242）| 覆盖主流用户，Kotlin 2.x、新 ToolWindow API 可用 |
| Git 调用 | **优先 git4idea**（IDE 内置），JGit 仅作 fallback | git4idea 已处理凭据、submodule 等边角 |
| 本地缓存 | SQLite + SQLDelight | 体积小、类型安全、可跨平台 |
| AI SDK | 自研薄封装（避免拉入 LangChain4j 重量级依赖） | 插件包体积敏感 |
| UI | Swing + JBComponents；图表用 JFreeChart 或自绘 | **不引入 JCEF**，避免与某些 IDE 发行版冲突 |
| 异步 | Kotlin Coroutines + IDE 的 `BackgroundableTask` | 避免 UI 卡顿 |

> ⚠️ **重要变更点 vs 参考稿**：
> - 参考稿提到 "JCEF + JavaFX"，**不推荐**。JavaFX 与 IDEA 插件生态有兼容性坑（当前项目脚手架就是 JavaFX，需要替换）；JCEF 会增大体积且部分用户禁用。
> - 现有 `build.gradle.kts` 是 JavaFX 模板，**需要整个替换为 IntelliJ Platform 插件模板**（见 §9 任务 T1）。

### 5.3 目录结构

```
git-insight/
├── build.gradle.kts                  # IntelliJ Platform 2.x 模板
├── settings.gradle.kts
├── gradle.properties                 # pluginVersion / platformVersion
├── src/main/
│   ├── kotlin/com/power/gitinsight/
│   │   ├── GitInsightPlugin.kt       # 入口、StartupActivity
│   │   ├── ui/
│   │   │   ├── tooltip/              # Enhanced Blame
│   │   │   ├── gutter/               # Hotspot
│   │   │   ├── toolwindow/           # Dashboard
│   │   │   └── checkin/              # Risk Dialog
│   │   ├── domain/
│   │   │   ├── blame/
│   │   │   ├── hotspot/
│   │   │   ├── risk/                 # RuleEngine + YAML loader
│   │   │   └── ai/                   # Orchestrator + Providers
│   │   ├── infra/
│   │   │   ├── git/                  # Git4IdeaAdapter, JGitAdapter
│   │   │   ├── storage/              # SQLDelight
│   │   │   └── telemetry/
│   │   └── settings/                 # Configurable, State
│   └── resources/
│       ├── META-INF/plugin.xml
│       ├── icons/
│       └── rules/default-risk.yml
└── src/test/kotlin/...
```

---

## 6. 服务端 & 后端方案（Cloudflare-first）

> 用户已有 Cloudflare 账户，本节按 **"Cloudflare 原生栈"** 设计 —— MVP 期间几乎零成本。

### 6.1 服务端职责分层

| 阶段 | 是否需要服务端 | 用途 |
|---|---|---|
| MVP（v0.1-v0.3） | **不需要** | 全本地，AI 调用走用户自己的 Key |
| 专业版（v0.4-v0.6） | 可选 | AI 网关（保护用户 API Key 不直连 / 计费） |
| 企业版（v0.7+） | 必须 | PR 分析 / 团队报表 / License |

### 6.2 Cloudflare 组件映射

| 需求 | Cloudflare 产品 | 说明 |
|---|---|---|
| 官网 / 文档 | **Cloudflare Pages** | 静态站，免费 |
| API 网关 | **Workers** | 路由 / 鉴权 / 限流 |
| AI 调用代理 | **AI Gateway** | 给 OpenAI/Claude/DeepSeek 加缓存、限流、可观测 |
| 内置 AI 选项 | **Workers AI** | 给免费用户提供"开箱即用"的小模型（Llama / Qwen） |
| 持久化（License/团队） | **D1**（SQLite） | 关系数据 |
| 缓存 / 配置下发 | **KV** | 规则 YAML 远程下发 |
| 大文件 / 日志 | **R2** | 对象存储，0 出口流量费 |
| 向量检索（v0.8+） | **Vectorize** | 代码语义检索 / 相似 commit 召回 |
| 异步任务 | **Queues** | PR 风险分析的后台处理 |
| Webhook 接收 | **Workers** + Queues | GitHub / GitLab webhook |

### 6.3 Cloudflare AI Gateway 接入示意

```
Plugin ──HTTPS──▶ gateway.gitinsight.app (Pages → Worker)
                          │
                          ├─ 鉴权 (License Key)
                          ├─ 缓存层 (KV / AI Gateway 自带缓存)
                          ├─ 路由：免费用户 → Workers AI
                          │       专业用户 → OpenAI / Claude (via AI Gateway)
                          └─ 计费 / 用量统计 (D1)
```

**成本预估（粗算）**：

| 项目 | 用量假设 | 月成本 |
|---|---|---|
| Pages | 静态站 | 免费 |
| Workers | < 10 万次/天 | 免费层内 |
| D1 | < 5 GB | 免费层内 |
| KV | < 1 GB | 免费层内 |
| AI Gateway | 任意次数 | 免费 |
| Workers AI | 按 Token 计费 | 100 用户 ≈ $5-20/月 |
| 域名 .com | 1 年 | ≈ ¥80/年 |

**结论**：MVP + 早期专业版可在 **¥100/年** 内运营（仅域名成本）。

---

## 7. 数据模型

### 7.1 本地（Plugin Side, SQLite）

```sql
-- 文件热度快照
CREATE TABLE file_hotspot (
  repo_id        TEXT NOT NULL,
  file_path      TEXT NOT NULL,
  modify_count   INTEGER DEFAULT 0,
  rollback_count INTEGER DEFAULT 0,
  conflict_count INTEGER DEFAULT 0,
  last_modified  INTEGER,        -- epoch ms
  hotspot_score  REAL,
  updated_at     INTEGER,
  PRIMARY KEY (repo_id, file_path)
);

-- 行级 Blame 缓存（按 commit 不变性缓存）
CREATE TABLE blame_cache (
  repo_id    TEXT NOT NULL,
  file_path  TEXT NOT NULL,
  commit_id  TEXT NOT NULL,
  payload    BLOB NOT NULL,      -- 序列化 BlameSnapshot
  created_at INTEGER,
  PRIMARY KEY (repo_id, file_path, commit_id)
);

-- 事故 commit 标记
CREATE TABLE incident_commit (
  repo_id   TEXT NOT NULL,
  commit_id TEXT NOT NULL,
  reason    TEXT,                 -- revert / hotfix / 手动标记
  marked_at INTEGER,
  PRIMARY KEY (repo_id, commit_id)
);
```

### 7.2 服务端（Cloudflare D1，企业版才需要）

```sql
-- 用户 / License
CREATE TABLE license (
  id            TEXT PRIMARY KEY,
  email         TEXT,
  tier          TEXT,             -- free / pro / enterprise
  expires_at    INTEGER,
  created_at    INTEGER
);

-- 跨仓库聚合的提交分析
CREATE TABLE commit_analysis (
  id           TEXT PRIMARY KEY,
  org_id       TEXT,
  repo_name    TEXT,
  commit_id    TEXT,
  author_email TEXT,
  risk_score   INTEGER,
  risk_tags    TEXT,              -- JSON
  created_at   INTEGER
);
CREATE INDEX idx_commit_org_repo ON commit_analysis(org_id, repo_name);

-- AI 调用计量
CREATE TABLE ai_usage (
  id          TEXT PRIMARY KEY,
  license_id  TEXT,
  provider    TEXT,
  model       TEXT,
  input_tok   INTEGER,
  output_tok  INTEGER,
  cost_cents  INTEGER,
  created_at  INTEGER
);
```

---

## 8. 安全 / 隐私

> 这是企业用户最关心的部分，必须前置设计。

| 项 | 策略 |
|---|---|
| 代码出域 | **默认本地分析**；AI 调用前给出明确开关；可配置"只对 Diff 出域，不出域全文" |
| API Key 保存 | 走 IDE 的 `PasswordSafe`（系统 keychain），不写明文配置 |
| 遥测 | **默认关闭**；开启后只上报功能埋点（不含代码、不含路径） |
| License | 本地校验 + 离线宽限期 7 天，不强依赖在线 |
| 企业私有化 | 提供 Docker Compose / Helm chart，Cloudflare 网关可替换为内网 Nginx + Ollama |
| SBOM | 发布时附带依赖清单 |
| 合规 | 列入路线图：SOC2 Type I（v1.0 后） |

---

## 9. 里程碑（执行计划）

> 估时按 **1 人全职** 计算，括号是日历周。

### Sprint 0：项目重构（1 周）

- [ ] **T1**：删除现有 JavaFX 脚手架，初始化 IntelliJ Platform 插件项目
  - 使用 `intellij-platform-gradle-plugin` 2.x
  - 最低 IDE：2024.2，Kotlin 2.1，JVM 21
- [ ] **T2**：配置 GitHub Actions（build / verifier / publish 到 Marketplace）
- [ ] **T3**：写第一个 `AnAction` Demo + ToolWindow 占位

### Sprint 1：Enhanced Blame（2 周）

- [ ] **T4**：`GitAdapter`（git4idea 优先，JGit fallback）
- [ ] **T5**：`BlameService` + SQLite 缓存层（SQLDelight）
- [ ] **T6**：`LineMarkerProvider` + RichTooltip UI
- [ ] **T7**：单元测试（Mock VirtualFile / Project）

### Sprint 2：Hotspot 热力图（2 周）

- [ ] **T8**：历史扫描后台任务（首次打开仓库时全量扫，之后增量）
- [ ] **T9**：评分算法 + 单测
- [ ] **T10**：Gutter 渲染 + 项目级 ToolWindow 视图

### Sprint 3：Commit Risk Score（1.5 周）

- [ ] **T11**：`CheckinHandler` 接入
- [ ] **T12**：规则引擎（默认 YAML + 用户覆盖）
- [ ] **T13**：Risk Dialog UI
- [ ] **T14**：设置面板（规则开关）

### Sprint 4：AI Commit Message（1.5 周）

- [ ] **T15**：`AiProvider` 抽象 + OpenAI / Claude / DeepSeek / Ollama 实现
- [ ] **T16**：Provider 配置 UI + `PasswordSafe`
- [ ] **T17**：Diff 截断 / Commit 风格采样
- [ ] **T18**：Commit 对话框集成

### Sprint 5：AI Diff Review（2 周）

- [ ] **T19**：Prompt 工程（Java/Kotlin/SQL 三大类）
- [ ] **T20**：Inline 评论 UI
- [ ] **T21**：Cloudflare AI Gateway 接入（专业版用户走网关）

### Sprint 6：发布 0.x（1 周）

- [ ] **T22**：图标 / 截图 / Marketplace 描述
- [ ] **T23**：官网（Cloudflare Pages，单页即可）
- [ ] **T24**：发布 v0.1 到 JetBrains Marketplace

**MVP 总计 ≈ 11 周**（约 2.5 个月，1 人全职）

### v0.7+ 企业版（额外 2-3 个月）

- PR 分析（GitHub / GitLab Webhook → Cloudflare Worker → 分析后回写 PR 评论）
- 团队治理报表（D1 + Pages 仪表盘）
- License / SSO
- 私有化部署包

---

## 10. 风险登记册

| # | 风险 | 等级 | 缓解 |
|---|---|---|---|
| R1 | 现有项目是 JavaFX 模板，方向错误 | 高 | Sprint 0 必须重建 |
| R2 | IDEA Plugin API 在主版本升级时不兼容 | 中 | 锁定 LTS 平台版本，CI 跑 plugin-verifier |
| R3 | 全量历史扫描性能（大仓库 > 50 万 commit） | 中 | 增量扫描 + 时间窗（默认近 1 年） |
| R4 | AI 输出质量不稳定 | 中 | 多 Provider 切换、Prompt 版本化、用户可关闭 |
| R5 | 用户对"代码出域"敏感 | 高 | 默认本地、Diff-only、出域前显式确认 |
| R6 | Marketplace 审核被拒 | 低 | 提前阅读 Marketplace Approval Guidelines，准备隐私声明 |
| R7 | 商业化路径走不通 | 中 | MVP 先免费跑用户量，再决定 Pro 功能切分 |

---

## 11. TODO / 决策记录（已全部决议）

> v0.3 起所有 TODO 已决，本章节保留作为决策依据存档。新决策请追加到顶部"决策记录"表。

### 11.1 产品方向

- [x] **P1**：~~插件名 GitInsight~~ → **GitInsight**（Marketplace 重名规避，备选 RiskDiff / ChangeGuard）
- [x] **P2**：**个人开发者优先**
  - 决策依据：① MVP 单机零服务端 ② 开发者群体获取成本最低 ③ 企业销售周期长，需要 PLG 路径
  - 路线含义：v0.4-v0.6 优先做 AI 功能，团队功能延后到 v0.7+
- [x] **P3**：**Open Core**
  - 核心（Blame 增强 / Hotspot / Risk Score）→ **Apache-2.0** 开源到 GitHub
  - AI Diff Review / 企业版 / 服务端代码 → 闭源
  - 价值：开源拉星获口碑，闭源部分变现，规避竞品 fork 风险

### 11.2 技术决策

- [x] **T-a**：最低 IDE 版本 = **2024.2**（build 242）
  - 覆盖率 vs 新 API 折中；plugin-verifier 跑 242-243 区间
- [x] **T-b**：v0.x **不支持 Android Studio**，v1.0 再评估
  - AS 平台版本滞后 6-12 个月；后端/全栈是核心目标用户
- [x] **T-c**：**SQLite + SQLDelight**
  - 类型安全、内置 schema 迁移；XML 序列化会随项目大小线性退化
- [x] **T-d**：JavaFX 脚手架删除（Sprint 0 / T1）

### 11.3 Cloudflare / 后端

- [x] **C-a**：域名暂缓，用 `gitinsight.pages.dev` 占位
- [x] **C-b**：**启用 Workers AI 兜底**
  - 默认模型：`@cf/qwen/qwen1.5-7b-chat-awq`（中文友好、免费层够用）
  - 配额：免费用户 30 次/天 AI Commit；超出引导填自己 Key 或升级 Pro
- [x] **C-c**：AI Gateway 路由 = **Pro 强制 / Free 可选**
  - Pro 用户：托管 Key 走 Gateway（统计、缓存、限流自动接管）
  - Free 用户：自带 Key 时给"直连 vs Gateway"开关，默认直连
- [x] **C-d**：MVP 期间不搭服务端，**但现在做两件事**：
  - 注册 Cloudflare Pages 项目 `gitinsight`（占坑、免费）
  - 注册 Workers 子域名 `api.gitinsight.pages.dev`
  - 正式接入 v0.4（与 AI Diff Review 同 Sprint）

### 11.4 商业化

#### B-a 功能切分

| 功能 | Free | Pro | Enterprise |
|---|:-:|:-:|:-:|
| Enhanced Blame | ✓ | ✓ | ✓ |
| Hotspot 热力图 | ✓ | ✓ | ✓ |
| Risk Score（规则引擎） | ✓ | ✓ | ✓ |
| AI Commit | ✓ (30 次/天 兜底) | ✓ 无限 | ✓ |
| AI Diff Review | — | ✓ | ✓ |
| 自定义规则 YAML | — | ✓ | ✓ |
| Cloudflare Gateway 托管 Key | — | ✓ | ✓ |
| 本地 Dashboard | ✓ | ✓ | ✓ |
| PR Webhook 风险分析 | — | — | ✓ |
| 团队治理报表 / Bus Factor | — | — | ✓ |
| SSO / 私有化部署 | — | — | ✓ |

#### B-b 定价

| 档位 | 国内价 | 海外价 |
|---|---|---|
| Free | ¥0 | $0 |
| **Pro 年付** | **¥199/年** | **$29/year** |
| Pro 月付 | ¥25/月（年化 ¥300，年付省 33%） | $3.99/month |
| Pro 早鸟首年 | **¥129**（上架后 3 个月，约 35% off） | $19 first year |
| Enterprise | 联系销售（参考 ¥1500/seat/年起） | Contact sales |

定价锚点：¥199 ≈ JetBrains 个人 All Products Pack（¥249/年）的 80%，低于 IDE 本身，开发者心理可接受。

#### B-b' 支付通道（国内 + 海外）

| 阶段 | 国内 | 海外 |
|---|---|---|
| **v0.1 上架时** | 爱发电（临时） | Stripe / LemonSqueezy |
| **v0.4 之前** | **个体工商户注册 → 微信支付 + 支付宝官方商户** | Stripe + LemonSqueezy |

- 微信支付申请前置条件：营业执照 + 对公账户 + ICP 备案站点
- **Sprint 0 期间需用户启动个体工商户注册流程**（独立于代码进度并行做，办理周期 1-3 周）

#### B-c OSS 维护者免费

- **采纳**
- 验证方式：公开 GitHub repo（≥ 50 stars）+ 用户邮箱与 commit author 一致
- 实现：服务端校验时返回 `tier=oss`，等价于 Pro

### 11.5 工程支撑

- [x] **E-a** 测试栈：
  - 单元测试：JUnit5 + **MockK**（Kotlin friendly）
  - 集成测试：**IntelliJ Platform Test Framework**（`BasePlatformTestCase` / `LightJavaCodeInsightFixtureTestCase`）
  - 覆盖率目标：Domain 层（Risk Engine / Hotspot 算法）≥ 80%
- [x] **E-b** CI = **GitHub Actions**
  - workflow：`pr.yml`（build + unit test） / `verify.yml`（每晚跑 plugin-verifier 跨 242-243） / `release.yml`（tag 触发发布 Marketplace）
- [x] **E-c** 遥测：
  - 自建 Cloudflare Worker `/telemetry` endpoint，写入 D1
  - **默认关闭**，首启弹窗征求同意
  - 仅上报：异常堆栈、IDE 版本、插件版本、匿名安装 ID
  - 绝不上报：代码内容、文件路径、用户名、Git URL

---

## 12. 下一步行动

§11 已全部决议，按以下顺序执行：

**立即可做（代码侧 / 由我执行）**
1. **Sprint 0 / T1**：删除 JavaFX 脚手架，初始化 IntelliJ Platform 插件项目
   - 用 `intellij-platform-gradle-plugin` 2.x
   - 包名：`com.power.gitinsight`
   - 最低 IDE：2024.2，Kotlin 2.1，JVM 21
2. **Sprint 0 / T2**：配置 GitHub Actions 三 workflow（pr / verify / release）
3. **Sprint 0 / T3**：HelloWorld `AnAction` + 空 ToolWindow 验证骨架

**并行做（账号 / 商务侧 / 需用户参与）**
4. **Cloudflare 占坑**（5 分钟）：登录 Cloudflare → Pages 创建项目 `gitinsight` → Workers 创建 `api-gitinsight`
5. **个体工商户注册**（1-3 周，并行不阻塞 MVP）：用于 Pro 上线后接入微信支付 / 支付宝
6. **GitHub 仓库**：建公开 repo `power/gitinsight`，准备 Apache-2.0 LICENSE + README 占位

**v0.1 发版前再做**
7. 200 字产品一句话介绍 + 3 张概念图（Marketplace 提交素材）
8. 隐私政策草案（Marketplace 审核要求）

---

## 附录 A：参考资料

- IntelliJ Platform SDK：https://plugins.jetbrains.com/docs/intellij/
- IntelliJ Platform Gradle Plugin 2.x：https://github.com/JetBrains/intellij-platform-gradle-plugin
- git4idea API：在 IDEA 源码中（`platform/vcs-impl`、`plugins/git4idea`）
- JGit：https://www.eclipse.org/jgit/
- Cloudflare Workers AI：https://developers.cloudflare.com/workers-ai/
- Cloudflare AI Gateway：https://developers.cloudflare.com/ai-gateway/
- SQLDelight：https://cashapp.github.io/sqldelight/

## 附录 B：术语表

| 术语 | 含义 |
|---|---|
| Blame | Git 行级追溯（谁、何时、哪个 commit） |
| Hotspot | 高频修改 / 高风险代码热点 |
| Risk Score | 单次提交的综合风险评分 |
| Bus Factor | 团队风险指标：项目仅依赖几个关键人 |
| AI Gateway | Cloudflare 提供的 AI 请求代理层 |
| Provider | AI 模型提供方（OpenAI / Claude / Ollama 等）的统一抽象 |
