# GitInsight 发版 Checklist

下一次发布插件到 JetBrains Marketplace 时，按此清单执行。

---

## 1. 发版前准备

- [ ] 确认所有改动已 commit 并 push 到 `master`（`git status` 干净）。
- [ ] 选定新版本号：必须**大于** Marketplace 上现有最高版本。
  - Marketplace 会拒绝重复版本（`already contains version X in channel`）。
- [ ] 编辑 `gradle.properties` 的 `pluginVersion`。
- [ ] 确认兼容性配置不变：
  - `pluginSinceBuild = 242`
  - `build.gradle.kts` 中 `untilBuild = provider { null }`（无上限，未来 IDE 不会被自动下架）。

## 2. 构建与校验

- [ ] `./gradlew clean buildPlugin`
  - 产物：`build/distributions/gitInsight-<version>.zip`
- [ ] `./gradlew verifyPlugin`
  - 必须 **Compatible**、0 deprecated API、0 problems。
- [ ] 解压/检查 zip 内 `META-INF/plugin.xml`：
  - `<version>` 与本次发版一致。
  - `<idea-version since-build="242" />`，无 `until-build`。

## 3. 发布

- [ ] 确认发布 token 已就绪（二选一）：
  - 环境变量 `PUBLISH_TOKEN`，或
  - 文件 `~/.gitinsight-publish-token`（`chmod 600`，仓库之外）。
  - 生成入口：https://plugins.jetbrains.com/author/me/tokens
- [ ] `bash scripts/publish.sh`
  - 脚本会读取 token 并跑 `clean buildPlugin publishPlugin`，token 不会被打印。
- [ ] 确认 `BUILD SUCCESSFUL`，无 `already contains version` 报错。

## 4. 发布后

- [ ] 提交版本号变更：`Bump plugin version to <version> for Marketplace upload`，push。
- [ ] 在 Marketplace 后台查看审核状态（需登录网页，自动化无法代劳）。
- [ ] **撤销/轮换发布 token**：用完即撤，避免泄漏。
  - https://plugins.jetbrains.com/author/me/tokens

## 5. License / Server 联动（仅在需要时）

- [ ] 若轮换签名密钥：
  - `gitinsight-server` 内 `npm run keygen` 重新生成密钥对。
  - 更新插件 `LicenseVerifier.kt` 的 `PUBLIC_KEY_SPKI_B64URL`（公钥）。
  - 私钥写入 `.dev.vars`（gitignored），通过 `bash scripts/set-secrets.sh` 推送到 Worker。
- [ ] 服务端改动：先 `npm run typecheck`（0 错误），再 `npm run deploy`。

---

## 常见坑

- **`already contains version X in channel`** → 版本号没递增，提高 `pluginVersion` 后重新构建发布。
- **`verifyPlugin` 下载 IDE 失败** → 保持 `ide(IntellijIdeaCommunity, platformVersion)`，不要用 `recommended()`（会拉一堆 IDE 且依赖网络）。
- **未签名包** → 当前 `signPlugin` 在缺少证书环境变量时会 SKIP，Marketplace 接受未签名上传，属正常。
- **1.1.x 启用付费档** → 需要把 `PREVIEW_WINDOW_OPEN` 翻为 `false` 并启用激活 UI（当前 1.0.x 全员 Pro Preview，激活入口关闭）。
