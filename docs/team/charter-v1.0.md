# 团队章程 v1.0

> 本文件是团队的治理基础，所有成员签字后生效。每个 Sprint 结束时回顾一次，必要时修订为新版本。

## 1. 团队信息

| 角色 | 姓名 | GitHub 账号 | 职责 |
|---|---|---|---|
| PO（产品负责人） | 张宇航 | @futureel | 需求、Backlog 优先级、验收标准、Review 演示 |
| SM（Scrum Master） | 待填 | 待填 | 流程、站会、看板、分支保护、CI 救火 |
| Developer | 待填 | 待填 | Q&A 模块（S2 核心） |
| Developer | 待填 | 待填 | Reputation 模块（S3） |
| Developer / QA | 待填 | 待填 | Discovery 模块 + 前端 + 测试覆盖率 |

## 2. 团队价值观

- **代码能跑 > 文档好看**：每个 Sprint 结束必须有可演示的增量。
- **测试是需求说明书**：先看红测试，再写实现，不为了过测试改断言。
- **PR 必须有人 review**：main 分支不接受直推，至少 1 人 LGTM 才合并。
- **问题早抛出来**：站会卡住了就说，不要憋到 Sprint 末。

## 3. 沟通约定

- **异步为主**：飞书/微信群留言，24 小时内回复；不要@所有人催。
- **Daily Standup**：每个实验日开始时 10 分钟，三个问题：昨天做了什么、今天要做什么、有什么阻碍。
- **Sprint 计划会**：Sprint 第 1 次课内，2 小时内确定本 Sprint 承诺。
- **Sprint 回顾会**：Sprint 最后一次课内，用 Start / Stop / Continue 模板，产出 2-3 条改进项。

## 4. 开发规约

- **分支命名**：`feature/<姓名>-<功能>`、`fix/<姓名>-<bug>`、`docs/<姓名>-<文档>`。
- **Commit 信息**：`<类型>: <描述>`，类型用 feat / fix / docs / refactor / test / chore。
- **PR 标题**：和 commit 同格式，PR 描述里写清楚"做了什么、为什么、怎么测的"。
- **代码格式**：后端用 IDEA 默认 + Spotless；前端 Prettier，pre-commit 钩子会拦。
- **不碰的东西**：不改已有测试断言、不删 outbox_event 表、不跨表前缀 JOIN（QA 表不能 JOIN REP 表）。

## 5. DoD（完成定义）

一个 Story 算"做完"，必须同时满足：

- [ ] 代码已合并到 main 分支
- [ ] 单元测试通过，领域层覆盖率 ≥70%
- [ ] ArchUnit 架构测试不挂
- [ ] PR 至少 1 人 review 并 LGTM
- [ ] Swagger 接口文档已更新
- [ ] 在本地或测试环境手动验证过主流程

## 6. 签字

| 成员 | 日期 | 签名（commit message 或 Markdown 签名） |
|---|---|---|
| 张宇航（PO） | 2026-10-22 | 张宇航 |
| 待填 | | |
| 待填 | | |
| 待填 | | |
| 待填 | | |
