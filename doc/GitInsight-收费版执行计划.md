# GitInsight — 收费版（Pro / Enterprise）功能设计 & 执行计划

> 版本：v0.2 草案
> 编写日期：2026-06-10（v0.2 折入关键 TODO 决议）
> 上游文档：`doc/GitInsight-需求执行计划.md`（§6 Cloudflare 架构 / §11.4 档位切分 / B-b 定价）
> 现状：核心免费功能已上线（1.0.1，已提交审核）；**Pro Preview 脚手架已落地**（`LicenseTier` / `LicenseSettings` / `LicenseSettingsConfigurable`，提交 `4275df3`）
> 目标：在不破坏 1.0.x 现有用户体验的前提下，定义 1.1.x（Pro）与 2.x（Enterprise）的**可执行落地路径**

---

## 0. 本文档定位

主执行计划（`GitInsight-需求执行计划.md`）已经决议了**商业模式**（Open Core）、**档位切分**（§11.4 B-a）、**定价**（B-b）、**Cloudflare 架构**（§6）。本文档**不重复**这些决策，而是把它们拆解为**收费版的工程落地步骤**，并标注当前仍未决的 TODO。

阅读顺序：
1. §1 现状盘点（已有什么）
2. §2 Pro 档（1.1.x）—— 单机 + 轻服务端
3. §3 Enterprise 档（2.x）—— 重服务端
4. §4 服务端落地（Cloudflare）
5. §5 计费 & License 系统
6. §6 里程碑
7. §7 未决 TODO 汇总

---

## 1. 现状盘点

### 1.1 已实现的收费相关脚手架（提交 4275df3）

| 组件 | 文件 | 当前行为 | 1.1.x 需要做的改造 |
|---|---|---|---|
| 档位枚举 | `domain/license/LicenseTier.kt` | `FREE` / `PRO_PREVIEW` / `PRO`；`PREVIEW_WINDOW_OPEN=true` 让 FREE 也解锁全部 | 增加 `ENTERPRISE`、`OSS`；翻转 `PREVIEW_WINDOW_OPEN=false` |
| 档位存储 | `domain/license/LicenseSettings.kt` | APP 级 `PersistentStateComponent`，存 `tier` + `licenseKey`（占位） | 接入真实 License 校验、过期时间、离线宽限 |
| 设置面板 | `ui/license/LicenseSettingsConfigurable.kt` | 只读 "Pro Preview" 横幅 + 禁用的 Key 输入框 | 启用 Key 输入 + 激活/校验按钮 + 状态显示 |
| 功能闸门 | `LicenseSettings.unlocksProFeatures()` | 全局返回 true（预览期） | 各 Pro 功能入口实际调用此方法做 gating |

> **关键设计**：1.1.x 启动 Pro 收费时，**唯一的破坏性改动是翻转 `PREVIEW_WINDOW_OPEN` 标志位**，其余是"启用已预留的接口"，不需要重写架构。

### 1.2 已实现、将被 gating 的功能

| 功能 | 现有实现类 | 计划档位 |
|---|---|---|
| AI Diff Review | `ui/ai/AiReviewAction.kt`、`domain/ai/AiReviewPrompt.kt` | Pro |
| 项目级 YAML 规则覆盖 | `domain/risk/RiskRulesProjectLoader.kt`、`RiskRulesYamlParser.kt` | Pro |
| AI Commit Message（无限） | `ui/ai/GenerateCommitMessageAction.kt`、`domain/ai/CommitMessagePrompt.kt` | Free 限额 / Pro 无限 |
| Cloudflare 托管 Key | `domain/ai/CloudflareWorkersAiProvider.kt` | Free 兜底 / Pro 走 Gateway |

### 1.3 尚未实现（需新建）

- AI Commit 免费配额计数（30 次/天，§11.3 C-b）
- License 在线校验客户端
- 服务端（Cloudflare Workers + D1 + KV）
- 计费通道接入（微信/支付宝/Stripe）
- Enterprise：PR Webhook、团队报表、SSO、私有化

---

## 2. Pro 档（1.1.x）执行计划

> 定位：**个人开发者付费**。单机为主，服务端仅做 License 校验 + AI Gateway。

### 2.1 P-1：功能闸门接线（Feature Gating）

把 `LicenseSettings.unlocksProFeatures()` 接到各 Pro 功能入口：

- [ ] **G1**：`AiReviewAction` — 非 Pro 时点击弹"升级"提示（**已决 TODO-A：可点击 + 弹升级页**，转化率优先），预览期不触发
- [ ] **G2**：`RiskRulesProjectLoader` — 非 Pro 时忽略 `.gitinsight/risk.yml`，回退到内置规则，并在 Risk Dialog 提示
- [ ] **G3**：AI Commit 配额 —— 见 P-2
- [ ] **G4**：统一 "升级到 Pro" 提示组件（`ui/license/UpgradePrompt.kt`，新建），所有闸门复用
- [ ] **G5**：`LicenseTier` 增加 `ENTERPRISE`、`OSS`，`unlocksProFeatures()` 覆盖新档

> ✅ TODO-A 已决：被拦截时**可点击但弹升级页**（含"填自己的 Key 免费用"出口），不做纯禁用。

### 2.2 P-2：AI Commit 免费配额（30 次/天）

- [ ] **Q1**：新建 `domain/ai/AiQuotaService.kt`（APP 级 `PersistentStateComponent`）
  - 状态：`{ date: String(yyyy-MM-dd), usedCount: Int }`
  - 仅对兜底 Provider 成功生成时 +1
  - 跨天自动重置
- [ ] **Q2**：仅对 **Cloudflare 兜底 Provider**（`id="cf-workers-ai"`）计数；用户自带 Key（OpenAI/Claude/DeepSeek/Ollama）**不计数、不限额**
- [ ] **Q3**：超额时弹窗：①填自己的 Key（免费无限）②升级 Pro（托管 Key 走 Gateway）
- [ ] **Q4**：Pro 用户 `unlocksProFeatures()==true` 时跳过配额检查

> ✅ TODO-B 已决：**本地计数**（防君子不防小人；免费兜底成本本就低，不值得为它每次联网）。

### 2.3 P-3：License 激活 UI

- [ ] **L1**：启用 `LicenseSettingsConfigurable` 的 Key 输入框 + "Activate" 按钮
- [ ] **L2**：调用 §5 License 校验客户端，成功后写入 `LicenseSettings.state.tier=PRO` + `expiresAt`
- [ ] **L3**：显示当前状态（tier / 到期日 / 离线宽限剩余天数）
- [ ] **L4**：`isModified()` / `apply()` 启用（当前是 no-op）

### 2.4 P-4：AI Gateway 托管 Key（Pro 专属）

- [ ] **GW1**：`CloudflareWorkersAiProvider` 扩展为可走 **AI Gateway 路由**（Pro 用 OpenAI/Claude via Gateway，Free 用 Workers AI 小模型）
- [ ] **GW2**：Pro 用户无需自带 Key —— 请求带 License Key，由 Worker 鉴权后注入托管 Key
- [ ] **GW3**：用量统计写 D1 `ai_usage` 表（§7.2）

> ✅ TODO-C 已决：托管 Key 设**软上限**（如 500 次/天）+ 公平使用条款，防滥用。

---

## 3. Enterprise 档（2.x）执行计划

> 定位：**团队 / 组织**。必须有服务端。对应主计划 v0.7+。

### 3.1 E-1：PR Webhook 风险分析

- [ ] GitHub / GitLab Webhook → Cloudflare Worker → Queue → 分析 → 回写 PR 评论
- [ ] 复用现有 `RiskEngine` 逻辑（见 TODO-D 决议：抽成共享规则定义）

> ✅ TODO-D 已决（推迟到 2.x 落地）：规则本就是声明式（条件→分值，主计划 §4.3 共 8 条），抽成**共享 YAML/JSON 规则定义**，Kotlin 端与 Worker(TS) 端各写**薄解释器**。避免双份硬编码逻辑，也不引入 JVM 容器（成本/冷启动）。不阻塞 Pro。

### 3.2 E-2：团队治理报表

- [ ] Bus Factor、高频事故作者、模块所有权
- [ ] 数据源：各成员插件上报 `commit_analysis`（§7.2）到 D1
- [ ] 展示：Cloudflare Pages 仪表盘（独立 web，非插件内）

> ✅ TODO-E 已决：成员数据上报需**组织管理员显式开启 + 成员明确同意**，默认关闭（沿用主计划 §8 隐私基线）。

### 3.3 E-3：SSO + 私有化部署

- [ ] SSO（SAML / OIDC）
- [ ] Docker Compose / Helm chart（Cloudflare 网关可替换为内网 Nginx + Ollama，见主计划 §8）

> ✅ TODO-F 已决：私有化用**离线 License 文件**（同 §5.1 Ed25519 签名串，公钥内置；不依赖在线激活服务器）。

---

## 4. 服务端落地（Cloudflare）

> 主计划 §6 已定架构，此处是**建仓建目录的执行步骤**。当前仓库**无服务端目录**（已确认）。

- [ ] **S1**：新建**独立私有 repo** `gitinsight-server`（闭源；签发私钥与公开插件仓库隔离）

> ✅ TODO-G 已决：**独立私有 repo**。Open Core 要求闭源代码与 Ed25519 签发私钥绝不进公开仓库。

- [ ] **S2**：Workers 项目（`wrangler`）：`/license/verify`、`/license/issue`、`/ai/proxy`、`/telemetry`（遥测端已设计，见主计划 §11.5 E-c）
- [ ] **S3**：D1 建表：`license` / `commit_analysis` / `ai_usage`（schema 见主计划 §7.2）
- [ ] **S4**：KV：规则 YAML 远程下发（可选，Pro+）
- [ ] **S5**：AI Gateway 配置（OpenAI/Claude/DeepSeek 加缓存限流）
- [ ] **S6**：Pages 占位站 `gitinsight.pages.dev`（主计划 C-d 已规划）

---

## 5. 计费 & License 系统

### 5.1 License Key 格式与校验

> ✅ TODO-H 已决：**Ed25519 离线签名串**。不引第三方 License SaaS（Keygen/LemonSqueezy License API）——它们强在线依赖，与主计划 §8 的 7 天离线宽限冲突且增加代码出域面。

- [ ] **License Key 生成**：服务端（`gitinsight-server`，持 Ed25519 私钥）签发离线可验签名串
  - payload：`tier | expiresAt | email`（紧凑编码后 + 签名）
  - 私钥仅存服务端 secret，公钥编入插件
- [ ] **客户端校验**：`domain/license/LicenseVerifier.kt`（新建）
  - 离线：用内置公钥验签 + 校验 `expiresAt`（满足 7 天宽限，无网也能用）
  - 在线（有网时附加）：调 `/license/verify` 查吊销黑名单
  - 本地缓存上次成功校验时间，用于离线宽限计时
- [ ] **防破解**：签名校验 + 不做强在线依赖（主计划 §8）

### 5.2 支付通道

> ✅ TODO-I 已决：**首款直接用微信支付 + 支付宝官方商户**（不走爱发电过渡）。

- [ ] **国内**：个体工商户注册 → **微信支付 + 支付宝官方商户**
- [ ] **海外**：Stripe / LemonSqueezy（无需 ICP 备案，可先行）
- [ ] **OSS 免费**（B-c）：GitHub repo ≥50 stars + 邮箱与 commit author 一致 → `tier=oss`

**⚠️ ICP 备案与 Cloudflare 架构冲突（必须先解决）**：国内网站支付要求购买页域名做 ICP 备案，而备案要求服务器在**中国大陆境内**；Cloudflare（境外）无法备案。
→ 决议：**购买页/官网单独放国内云（阿里云/腾讯云轻量，约 ¥100/年）并备案**，插件 / AI 网关 / License 服务仍留 Cloudflare。

**用户需提供的资质（办理顺序，越早越好）**：
1. 个体工商户营业执照（市场监督管理局 / 一网通办，1–3 周）
2. 法人身份证
3. 结算银行账户（个体户对公户或法人卡）
4. ICP 备案域名（买国内云 + 域名 → 工信部备案，7–20 天，需执照）
5. 执照 + 备案下来后：微信商户平台 `pay.weixin.qq.com` / 支付宝开放平台 `open.alipay.com` 提交资质（3–7 天）

> TODO-J：购买流程 —— 在 IDE 内跳转浏览器到购买页，付款后回填 Key？还是邮件发 Key？建议跳转 + 邮件双通道，待确认。

---

## 6. 里程碑

### Milestone 1.1.0：Pro 启动（核心）

1. 翻转 `LicenseTier.PREVIEW_WINDOW_OPEN = false`
2. P-1 功能闸门接线（G1–G5）
3. P-2 AI Commit 配额（Q1–Q4）
4. P-3 License 激活 UI（L1–L4）
5. §5 License 校验客户端 + 服务端 `/license/verify` + `/license/issue`（S1–S3）
6. 支付通道至少一条打通：国内微信/支付宝官方商户（依赖个体户 + ICP 备案），或海外 Stripe 先行
7. `change-notes` 更新 + 版本号 1.1.0

> **门槛**：Pro 启动前，§5 License 系统 + 至少一条支付通道必须就绪，否则无法收款。
> **关键路径**：个体户注册 + ICP 备案（§5.2）耗时最长，需**立即并行启动**，否则会阻塞国内收款。

### Milestone 1.2.x：Pro 增强

- P-4 AI Gateway 托管 Key（GW1–GW3）
- KV 规则远程下发

### Milestone 2.0.0：Enterprise

- E-1 PR Webhook
- E-2 团队报表
- E-3 SSO + 私有化

---

## 7. 未决 TODO 汇总

| 编号 | 待决问题 | 阻塞项 | 状态 |
|---|---|---|---|
| TODO-A | Pro 功能被拦截时 UX | P-1 | ✅ 已决：可点击 + 弹升级页 |
| TODO-B | AI Commit 配额本地 vs 服务端计数 | P-2 | ✅ 已决：本地计数 |
| TODO-C | Pro 托管 Key 成本封顶 | P-4 | ✅ 已决：软上限 500/天 + 公平使用 |
| TODO-D | 服务端规则引擎实现方式 | E-1 | ✅ 已决：共享规则定义 + 两端薄解释器（2.x 落地） |
| TODO-E | 团队数据上报默认开关 | E-2 | ✅ 已决：管理员显式开启 + 成员同意 |
| TODO-F | 私有化离线 License 校验 | E-3 | ✅ 已决：离线 License 文件（Ed25519） |
| TODO-G | 服务端代码仓库归属 | S1 | ✅ 已决：独立私有 repo `gitinsight-server` |
| TODO-H | License Key 格式 | §5.1 | ✅ 已决：Ed25519 离线签名串 |
| TODO-I | 首款支付通道 | §5.2 | ✅ 已决：微信/支付宝官方商户（待用户启动注册 + 备案） |
| TODO-J | 购买流程（IDE 跳转 / 邮件发 Key） | §5.2 | ⏳ 待确认（建议跳转 + 邮件） |

> 仅剩 **TODO-J** 未决（不阻塞代码起步）。下一关键动作在用户侧：启动个体户注册 + ICP 备案（§5.2）。

---

## 附录：与主计划的引用映射

| 本文档 | 主计划 |
|---|---|
| §2 Pro 功能 | §11.4 B-a 表（Pro 列） |
| §3 Enterprise | §3.3 v0.7+、§11.4 B-a（Enterprise 列） |
| §4 Cloudflare | §6 服务端方案 |
| §5.1 License | §8 安全/隐私（离线宽限 7 天） |
| §5.2 支付 | §11.4 B-b / B-b' / B-c |
| §7.2 D1 表 | §7.2 数据模型 |
