Git 智能分析插件（Git Insight）需求设计计划

建议定位：

企业级 Git 智能分析 + AI 风险治理插件

不是单纯：

Git 工具

而是：

代码变更风险分析平台

这个方向更容易商业化。

一、插件定位

插件名建议：

中文：

Git洞察
提交雷达
代码脉搏
变更哨兵

英文：

GitInsight
CommitRadar
CodePulse
RiskDiff

我更推荐：

GitInsight

简洁且专业。

二、产品目标

解决几个核心问题：

1. 谁改坏了代码？

传统 Git：

git blame

只能看：

谁改了

但无法分析：

风险有多大
2. 哪个 PR 风险最高？

例如：

修改金额计算
修改库存
修改支付
修改锁逻辑
修改 SQL

插件自动：

风险评分
3. 哪些代码是高危核心代码？

识别：

高频修改
高频事故
高频回滚
多人频繁冲突

形成：

热力图
三、核心功能模块
模块1：Git 增强分析（MVP）

最先做。

功能1：增强版 Blame

鼠标放到代码：

显示：

作者
时间
Commit Message
修改次数
最近事故
风险等级

类似：

高风险代码
最近7天被修改8次
功能2：代码热力图

编辑器侧边栏：

颜色：

绿色：稳定
黄色：频繁修改
红色：高危热点

算法：

修改次数
×
最近时间
×
冲突次数
×
回滚次数
功能3：提交风险评分

提交前自动分析：

Risk Score：82

规则：

规则	分值
修改支付代码	+30
修改库存代码	+20
修改SQL	+10
删除代码	+10
涉及多模块	+15
无测试文件	+20
功能4：AI Commit Message

自动生成：

fix(payment):
修复订单超时状态同步问题

支持：

OpenAI
Claude
DeepSeek
本地 Ollama
模块2：团队治理（企业版）
功能1：PR 风险分析

自动分析：

PR 风险等级：High

原因：

涉及：
- 金额计算
- 分布式锁
- 核心SQL
  功能2：事故代码识别

识别：

回滚过的 commit

标记：

事故代码
功能3：代码所有权分析

例如：

订单模块：
张三 80%
李四 15%
其他 5%

解决：

Bus Factor（人员风险）
功能4：团队统计

统计：

提交质量
高频 Bug 作者
高频回滚
高风险提交趋势
模块3：AI 智能分析（高级版）

这个是壁垒。

AI Review

自动识别：

Java
空指针
BigDecimal 精度
并发问题
死锁
锁粒度
SQL

识别：

全表扫描
无索引
delete/update 无 where
Spring

识别：

事务失效
循环依赖
Bean 生命周期问题
四、IDEA 技术架构
1. 插件架构
   IDEA Plugin
   ├── Git Listener
   ├── PSI Analyzer
   ├── Diff Engine
   ├── Risk Engine
   ├── AI Engine
   ├── Local Cache
   └── UI Layer
2. 核心技术栈
   IDE SDK
   IntelliJ Platform SDK

官方：

JetBrains Plugin SDK Docs
Git

调用：

JGit

官方：

JGit
AST/代码分析

使用：

PSI + AST
AI

推荐：

Spring AI

或者：

LangChain4j

官方：

LangChain4j
UI

推荐：

ToolWindow + JCEF

实现：

风险面板
图表
热力图
五、插件目录结构
git-insight/
├── actions/
├── git/
├── psi/
├── risk/
├── ai/
├── ui/
├── cache/
├── api/
└── plugin.xml
六、数据库设计（服务端）

如果做企业版。

表1：commit_analysis
CREATE TABLE commit_analysis (
id BIGINT PRIMARY KEY,
repo_name VARCHAR(100),
commit_id VARCHAR(64),
author VARCHAR(64),
risk_score INT,
risk_tags JSON,
created_at DATETIME
);
表2：file_hotspot
CREATE TABLE file_hotspot (
id BIGINT PRIMARY KEY,
file_path VARCHAR(500),
modify_count INT,
rollback_count INT,
conflict_count INT,
hotspot_score INT
);
七、服务器架构
MVP 阶段（个人开发）

其实：

不需要服务器

插件本地运行即可。

AI 调用：

直接调用 OpenAI API

即可。

企业版才需要服务端

服务端负责：

AI 分析
团队统计
PR 分析
风险中心
License
用户体系
八、服务器成本
第一阶段（个人版）
完全可以 0 服务端

成本：

项目	费用
域名	60~100元/年
官网静态页	免费
插件市场	免费
第二阶段（轻量企业版）
推荐配置
服务器

推荐：

腾讯云轻量应用服务器
阿里云 ECS

配置：

2核4G
5M带宽

成本：

50~120元/月

足够：

100~300人团队
数据库

推荐：

MySQL 8
Redis
对象存储

日志：

OSS
COS

成本：

每月几块钱
AI 成本

如果调用：

OpenAI

成本主要是：

Token

例如：

100人团队
每月：
200~1000元
更推荐本地模型

企业更喜欢：

Ollama + Qwen3

部署：

RTX3060

即可。

九、域名方案
官网域名

推荐：

gitinsight.ai
gitradar.ai
codepulse.ai
riskdiff.com
国内购买

推荐：

阿里云万网
腾讯云域名注册
域名成本
域名	年费用
.com	60~100
.ai	300~800
.dev	100~200

建议：

先买 .com
十、开发周期
MVP（一个人）
第一阶段（2周）

完成：

Git监听
Blame增强
风险评分
第二阶段（2周）

完成：

热力图
ToolWindow
Diff分析
第三阶段（2周）

完成：

AI Commit
AI 风险分析
企业版

大约：

2~4个月
十一、商业化路径（重点）
免费版

功能：

热力图
blame增强
commit分析

用于：

获取用户
专业版（99~299/年）

功能：

AI Review
风险分析
PR治理
企业版（1万~20万/年）

功能：

私有化部署
团队治理
风险中心
统计报表