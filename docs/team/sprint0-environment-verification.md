# Sprint 0 环境验证记录

> 负责人：陈洲（SM）　周期：第 1–2 周（2026-09-01 ～ 2026-09-13）
> 目标：全员本地环境跑通，hello PR 合并，main 分支保护就位。本文件是三项验收的证据汇总。

## 1. 工具链版本（已验证可用）

| 工具 | 版本 | 用途 | 验证命令 |
|---|---|---|---|
| Git | 2.55.0 (Windows) | 版本管理 | `git --version` |
| JDK | Temurin 21.0.13 | 后端编译/运行（项目要求 Java 21） | `java -version` |
| Maven | 3.9.16 | 后端构建 | `mvn -version` |
| Node.js | 22.23.2 | 前端构建 | `node --version` |
| npm | 10.9.8 | 前端依赖 | `npm --version` |
| Docker Desktop | 待安装（见第 4 节） | 中间件与镜像构建 | `docker --version` |

## 2. 后端验证

### 2.1 编译：通过

```
mvn -B -f backend/pom.xml clean compile
... 27 个模块全部 SUCCESS ...
BUILD SUCCESS
```

### 2.2 单元测试：红是预期，不是环境故障

起始框架的 107 个 TODO 方法对应 38 个尚未实现的测试，首次运行必须看到失败，
证明测试框架本身工作正常。本地实测分布（domain 层）：

| 模块 | 测试数 | 通过 | 失败 | 说明 |
|---|---|---|---|---|
| co-shared-kernel | 2 | 2 | 0 | 全绿 |
| co-identity-domain | 9 | 9 | 0 | 参考实现，全绿 |
| co-qa-domain | 18 | 3 | 15 | 全部为 `UnsupportedOperationException: TODO(S2)` |
| co-reputation-domain | 15 | 1 | 14 | 全部为 `TODO(S3)` |
| co-discovery-domain | 5 | 0 | 5 | 全部为 `TODO(S3)` |

失败用例的异常类型统一为 `UnsupportedOperationException`，来自框架预置的 TODO 占位方法，
与 README 第 8 节"常见坑"描述一致。后续 Sprint 按红 → 绿 → 重构逐个消灭。

## 3. 前端验证

```
cd frontend
npm install          # 71 个依赖安装成功
npm run typecheck    # tsc --noEmit，无类型错误
npm run build        # tsc -b && vite build，42 个模块转换成功
```

构建产物：`dist/assets/index-*.js 178.38 kB（gzip 58.21 kB）`，构建通过。

## 4. Docker 环境（本机待办，不阻塞代码任务）

本机当前未安装 Docker Desktop / WSL2，compose 中间件暂无法本地拉起。
安装步骤（需管理员权限，安装后重启）：

1. 启用 WSL2：管理员 PowerShell 执行 `wsl --install`，重启；
2. 安装 Docker Desktop：`winget install Docker.DockerDesktop`，启动后确认 Settings → General → Use WSL 2 based engine 已勾选；
3. 验证：
   ```
   docker compose -f deploy/docker-compose.yml up -d mysql redis
   docker compose -f deploy/docker-compose.yml ps
   ```

Docker 相关的镜像构建与集成测试在 GitHub Actions CI 中已有 ubuntu 环境验证，不依赖本机 Docker。

## 5. hello PR 验收

- 陈洲 hello PR：#14 `feature/chenzhou-hello` → main，已合并
  （提交 c0e08f5 `docs: 新增 hello-chenzhou.md 陈洲自我介绍`，合并提交 1a940cd）。
- 团队其余成员 hello PR 均已合并：#12（袁友为）、#13（彭天赐）；张宇航为仓库 owner。
- 全员完成"克隆 → 建分支 → 提交 → PR → 合并"流程演练，Git 基础链路打通。

## 6. 分支保护验收

要求的配置项与核对结果见 [branch-protection.md](branch-protection.md)。
核心规则：main 禁止直推、PR 必须经评审、CI 通过才可合并、管理员同样受约束。

## 7. 结论

| Sprint 0 任务 | 状态 |
|---|---|
| 跑通本地环境（Docker + 后端 + 前端） | 后端/前端已跑通；Docker 待本机安装（CI 已覆盖） |
| 发 hello PR 并合并 | 已完成（#14） |
| 确认分支保护配置正确 | 见 branch-protection.md，配置项已核对 |
