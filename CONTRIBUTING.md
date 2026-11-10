# 贡献指南（Git 工作流规约）

> 维护人：陈洲（SM）　本文件是"所有代码走 PR、不直推 main"的执行细则，与团队章程、分支保护规则配套。

## 1. 分支模型

| 分支 | 用途 | 保护 |
|---|---|---|
| `main` | 始终可发布的主干 | 受保护，禁止直推/强推/删除 |
| `feature/<姓名>-<功能>` | 新功能 | 不保护，自由推送 |
| `fix/<姓名>-<bug>` | 缺陷修复 | 不保护 |
| `docs/<姓名>-<文档>` | 文档变更 | 不保护 |
| `chore/<姓名>-<事项>` | 构建/工具链/配置 | 不保护 |

分支名一律小写、用连字符分隔；一个分支只做一件事，完成后删除。

## 2. 所有变更必须走 PR（强制）

1. 从最新 `main` 切出分支：`git checkout main && git pull && git checkout -b feature/chenzhou-xxx`；
2. 小步提交，提交信息遵循第 3 节；
3. 推送分支：`git push -u origin <分支名>`；
4. 在 GitHub 上创建 Pull Request，base 选 `main`；
5. 描述按 **做了什么 / 为什么 / 怎么测** 三段式填写（Sprint 1 回顾会改进项 S2）；
6. 至少 1 名成员 review 并批准（LGTM），CI 全绿；
7. 满足条件后合并，优先使用 **Squash and merge** 保持主干历史整洁，合并后删除分支。

**禁止**：绕过 PR 直接 `git push origin main`、用 `--force` 强推、本地合并后直推。
分支保护会在服务端拒绝这些操作；管理员同样受约束。

## 3. 提交信息规范

格式：`<类型>: <描述>`

| 类型 | 用途 |
|---|---|
| feat | 新功能 |
| fix | 缺陷修复 |
| docs | 文档 |
| refactor | 重构（不改变外部行为） |
| test | 测试相关 |
| chore | 构建、依赖、工具链、配置 |

示例：`feat: 实现 Question 发布的标签归一化规则`。描述用中文或英文均可，要说清"做了什么"，不写无意义的 update、fix bug。

## 4. 本地质量门禁（提交前自动执行）

仓库已配置 pre-commit 钩子（husky + lint-staged），`git commit` 时自动对暂存文件执行：

- 前端 `*.ts/tsx`：`eslint --fix` + `prettier --write`；
- 前端 `*.css/json/md`：`prettier --write`。

钩子报错会阻止提交，先修复再提交。如遇紧急情况确需临时跳过（不推荐），
可用 `git commit --no-verify`，但该提交在 PR 评审时会被重点检查，且不得用于绕过 CI。

## 5. 推送前自检（Sprint 1 回顾会改进项 P1）

- 后端：`mvn -f backend/pom.xml verify`；
- 前端：`npm run typecheck && npm run lint && npm run build`；
- 本地验证通过后再推送，避免把红的 CI 留给别人。

## 6. 代码评审入口

评审时对照《代码评审清单 v1.0》（Sprint 3 起位于 `docs/review/code-review-checklist-v1.0.md`），
重点检查架构约束、测试、安全与可读性，而不只是格式。
