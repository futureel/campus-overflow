# SonarQube / SonarCloud 接入指南（质量门禁）

> 维护人：陈洲（SM）　Sprint 3 交付
> 目标：把持续代码质量扫描接入 CI，对**缺陷、漏洞、代码异味、重复率、覆盖率**设置统一质量门禁（Quality Gate），不达标不合并。

## 1. 现状与默认方案

项目默认接入 **SonarCloud**（托管版 SonarQube），相关参数已在 `backend/pom.xml` 的 `<properties>` 中配置：

| 属性 | 值 | 说明 |
| --- | --- | --- |
| `sonar.host.url` | `https://sonarcloud.io` | 服务地址（自建时改这里） |
| `sonar.organization` | `futureel` | SonarCloud 组织 Key |
| `sonar.projectKey` | `futureel_campus-overflow` | 项目唯一 Key |
| `sonar.java.coveragePlugin` | `jacoco` | 覆盖率来源 |
| `sonar.coverage.jacoco.xmlReportPaths` | `${project.basedir}/target/site/jacoco/jacoco.xml` | JaCoCo 报告路径 |

CI 侧（`.github/workflows/ci.yml`）在后端流水线末尾执行 `mvn sonar:sonar`，并通过环境变量 `SONAR_TOKEN` 认证；**未配置令牌时该步骤自动跳过**，不影响其余构建。

## 2. 一次性配置（由仓库 Owner/管理员执行）

### 2.1 在 SonarCloud 准备组织与项目

1. 用 GitHub 账号登录 https://sonarcloud.io ，首次进入按向导创建组织，Organization Key 填 **`futureel`**（须与 pom 中 `sonar.organization` 一致）。
2. 选择 **Analyze new project → Import an organization from GitHub**，授权后勾选仓库 `campus-overflow` 导入；生成的 Project Key 需为 **`futureel_campus-overflow`**（若不一致，改 pom 中的 `sonar.projectKey` 对齐）。
3. 在项目 **Administration → Configuration → New Code** 中，把“新代码定义”设为 `Previous version`（课程按 Sprint 演进，便于聚焦增量质量）。

### 2.2 生成认证令牌并写入仓库 Secrets

1. SonarCloud 右上角头像 → **My Account → Security → Generate Tokens**，令牌类型选 **Project Analysis Token**，复制生成的令牌（仅显示一次）。
2. 打开 GitHub 仓库 **Settings → Secrets and variables → Actions → New repository secret**：
   - Name：`SONAR_TOKEN`
   - Secret：粘贴上一步令牌。
3. 无需在任何文件中明文保存令牌；CI 通过 `env.SONAR_TOKEN` 读取。配置完成后，下次推送/PR 即自动执行扫描。

### 2.3 配置质量门禁（Quality Gate）

在 SonarCloud 项目 **Quality Gate** 中使用内置 `Sonar way`（推荐），其对“新代码”的核心要求包括：

- 覆盖率 ≥ 80%；
- 重复率 ≤ 3%；
- 无新增 Blocker/Critical 问题、无新增漏洞与安全热点未处理。

如需贴合课程节奏，可复制后自定义阈值，但**不得整体关闭门禁**。门禁结果会回写到 PR 的 Check 项，配合分支保护可设为“必须通过才能合并”。

## 3. 本地运行扫描（可选，用于提交前自查）

前置：已本地执行过 `mvn verify` 生成 JaCoCo 报告。

```bash
# Linux / macOS（在仓库根目录）
mvn -B -f backend/pom.xml verify sonar:sonar -Dsonar.token=你的令牌

# Windows PowerShell
$env:SONAR_TOKEN="你的令牌"
mvn -B -f backend/pom.xml verify sonar:sonar
```

扫描完成后在 SonarCloud 项目页查看结果；本地令牌请勿写入代码或提交记录。

## 4. 切换到自建 SonarQube（若课程提供本地服务器）

若使用自建 SonarQube 而非 SonarCloud，只需改两处，CI 步骤无需变动：

1. 改 `backend/pom.xml`：将 `sonar.host.url` 改为自建地址（如 `http://sonar.campus.local:9000`），并删除/忽略 `sonar.organization`（自建版默认 `default-organization`，组织 Key 以服务器实际为准）。
2. 在自建 SonarQube **My Account → Security** 生成令牌，同样以 `SONAR_TOKEN` 写入 GitHub Secrets；确保 GitHub Actions 的 Runner 能网络访问该 SonarQube 地址。

## 5. 待执行说明（当前环境约束）

- 本仓库当前**尚未提供 `SONAR_TOKEN`**，因此 CI 中 Sonar 步骤处于“配置就位、条件跳过”状态；按第 2 节配置令牌后即自动生效，无需再改流水线。
- 质量门禁作为合并条件的“强制阻断”需在分支保护中勾选对应的 Sonar Check，该设置由仓库 Owner（@futureel）完成（陈洲无管理员权限），参见 `docs/team/branch-protection.md`。
