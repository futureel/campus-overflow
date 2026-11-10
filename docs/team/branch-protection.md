# main 分支保护配置说明

> 负责人：陈洲（SM）
> 目的：保证"所有代码走 PR、不直推 main"，把团队章程第 2、4 节的约定变成仓库级强制规则。

## 1. 目标配置项（main 分支）

| 规则 | 要求值 | 对应章程条款 |
|---|---|---|
| Require a pull request before merging | 启用 | main 不接受直推 |
| Required approvals | ≥ 1 人批准（LGTM） | PR 必须有人 review |
| Dismiss stale pull request approvals | 启用（推新提交后旧批准失效） | 防止批准与最终代码不一致 |
| Require status checks to pass before merging | 启用 | CI 全绿才能合并 |
| Require branches to be up to date | 启用（strict 模式） | 合并前必须与 main 同步 |
| Required status checks | `后端构建与验证`、`前端构建`、`容器镜像构建` | 对应 ci.yml 的三个 job |
| Include administrators | 启用 | 管理员/PO 同样受约束，无例外 |
| Allow force pushes | 禁用 | 禁止强推覆盖历史 |
| Allow deletions | 禁用 | 禁止删除 main |
| Restrict who can push | 不额外限制（配合 PR 规则即无人可直推） | — |

## 2. 配置方式（需仓库 admin 权限）

### 方式 A：GitHub 网页

Settings → Branches → Add branch protection rule → Branch name pattern 填 `main` →
按第 1 节逐项勾选 → Create → 弹窗确认 Apply rules to administrators。

### 方式 B：gh CLI（一条命令完成）

```bash
gh api -X PUT repos/futureel/campus-overflow/branches/main/protection \
  -f required_status_checks.strict=true \
  -f required_status_checks.contexts[]='后端构建与验证' \
  -f required_status_checks.contexts[]='前端构建' \
  -f required_status_checks.contexts[]='容器镜像构建' \
  -F enforce_admins=true \
  -f required_pull_request_reviews.required_approving_review_count=1 \
  -F required_pull_request_reviews.dismiss_stale_reviews=true \
  -F required_pull_request_reviews.require_code_owner_reviews=false \
  -f restrictions= \
  -F allow_force_pushes=false \
  -F allow_deletions=false
```

## 3. 核对方法

```bash
# 读取生效中的保护规则，返回 JSON 即表示保护已启用
gh api repos/futureel/campus-overflow/branches/main/protection

# 反向验证：直接向 main 推送应被拒绝
git push origin main
# 预期：remote: error: GH006: Protected branch update failed ...
```

## 4. 其他分支建议

- `develop`（如启用）：同样要求 PR + 状态检查，但不强制管理员规则；
- `sprint/**`、`feature/**`、`fix/**`：不设保护，开发者可自由推送，合并入口统一受 main 保护约束。

## 5. 核对结论

配置项已按第 1 节定义；实际写入仓库需陈洲账号具备 admin 权限（或由 PO @futureel 执行第 2 节命令）。
执行后用第 3 节命令留存返回 JSON 作为配置证据。
