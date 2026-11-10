# CampusOverflow 运维手册 v1.0（Runbook）

> 维护人：陈洲（SM）　Sprint 4 交付
> 适用对象：负责部署与值班的团队成员、课程服务器管理员
> 部署方式：Docker Compose 容器化，全部命令在仓库根目录执行；下文 `compose` 均指
> `docker compose -f deploy/docker-compose.yml`。

---

## 1. 系统概览

| 服务 | 容器名 | 端口 | 作用 |
| --- | --- | --- | --- |
| MySQL 8.0 | co-mysql | 3306 | 业务数据，utf8mb4，ngram 中文分词，UTC |
| Redis 7 | co-redis | 6379 | 缓存/列表缓存，AOF 持久化 |
| 后端 | co-backend | 8080 | Spring Boot 模块化单体，jlink 精简 JRE |
| Web/Nginx | co-web | 80 | 托管前端静态资源 + 反向代理 `/api` |
| Prometheus | co-prometheus | 9090 | 指标采集（15s） |
| Grafana | co-grafana | 3000 | 监控仪表盘 |

- 用户访问入口：`http://<服务器IP>/`（Nginx，校园网经 80 端口）；接口经同源 `/api/`。
- 监控入口：Grafana `http://<服务器IP>:3000`，默认账号 `admin`，密码由 `GRAFANA_PASSWORD` 决定。
- 数据持久化卷：`mysql-data`、`redis-data`、`prometheus-data`、`grafana-data`。

## 2. 前置要求与环境变量

- 服务器已安装 Docker Engine 与 Compose 插件；仓库已克隆到部署目录（默认 `/opt/campus-overflow`）。
- 可选环境变量（写入部署目录 `.env`，不入库；不设则用括号内默认值）：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `MYSQL_ROOT_PASSWORD` | root_dev_pwd | MySQL root 密码，生产必须改 |
| `DB_PASSWORD` | campus_dev_pwd | 业务库密码，生产必须改 |
| `GRAFANA_PASSWORD` | admin | Grafana 管理员密码，生产必须改 |
| `SPRING_PROFILES` | prod,demo | 正式使用去掉 `demo`（演示数据） |
| `COOKIE_SECURE` | false | 启用 HTTPS 后改 true |
| `APP_VERSION` | latest | 本地构建镜像的版本标签 |
| `BACKEND_IMAGE` / `WEB_IMAGE` | 本地构建镜像名 | 自动部署时指向 GHCR 镜像 |

## 3. 启动

### 3.1 首次部署（本地构建镜像）

```bash
cd /opt/campus-overflow
# 1) 准备 .env（生产环境修改密码），示例：
#    cp deploy/.env.example .env  &&  vi .env
# 2) 构建并后台启动全部 6 个服务（含健康检查与依赖顺序）
docker compose -f deploy/docker-compose.yml up -d --build
# 3) 观察启动状态，等待各服务 healthy
docker compose -f deploy/docker-compose.yml ps
```

### 3.2 日常启动（已构建过）

```bash
docker compose -f deploy/docker-compose.yml up -d
```

### 3.3 仅启动应用与中间件（不启动监控）

```bash
docker compose -f deploy/docker-compose.yml up -d --build mysql redis backend web
```

### 3.4 启动后验证

```bash
# 后端健康检查应返回 {"status":"UP"}
curl -s http://localhost:8080/actuator/health
# Nginx 健康端点
curl -s http://localhost/health
# 浏览器打开 http://<服务器IP>/ 验证页面与登录
```

## 4. 停止

```bash
# 优雅停止全部服务（保留数据卷）
docker compose -f deploy/docker-compose.yml down
# 仅停止某个服务，例如临时停后端
docker compose -f deploy/docker-compose.yml stop backend
# 停止并删除数据卷（危险！仅在确认要清空数据时使用，会删除数据库）
docker compose -f deploy/docker-compose.yml down -v
```

## 5. 状态、日志与进入容器

```bash
# 服务状态与健康检查
docker compose -f deploy/docker-compose.yml ps
# 实时查看某服务日志（后端示例）
docker compose -f deploy/docker-compose.yml logs -f backend
# 最近 200 行、带时间戳
docker compose -f deploy/docker-compose.yml logs --tail=200 backend
# 进入容器排查
docker exec -it co-backend sh
```

## 6. 回滚

### 6.1 应用版本回滚（镜像级，最快）

CI 每次发布在 GHCR 保留以 commit SHA 为标签的镜像，部署目录 `VERSION` 记录当前版本。回滚到上一版本：

```bash
cd /opt/campus-overflow
# 1) 确认可用的历史镜像标签（GHCR 包页面或）
docker images | grep campus-overflow
# 2) 指定要回滚的镜像（用对应 SHA 标签），重新拉起
export BACKEND_IMAGE=ghcr.io/<owner>/campus-overflow-backend:<旧SHA>
export WEB_IMAGE=ghcr.io/<owner>/campus-overflow-web:<旧SHA>
docker compose -f deploy/docker-compose.yml pull
docker compose -f deploy/docker-compose.yml up -d --remove-orphans
echo "<旧SHA>" > VERSION
```

### 6.2 数据库回滚（谨慎）

- 应用回滚优先选择**兼容旧库结构**的版本；Flyway 迁移默认只增不回退，不要手动改库结构。
- 回滚前先备份（见第 8 节）。若确需回退迁移，使用回滚前的逻辑备份恢复，而非删除迁移记录。

### 6.3 回滚后验证

按 3.4 验证健康检查与核心链路（登录、提问、投票、通知），并在 Grafana 确认指标恢复正常。

## 7. 故障处置

| 现象 | 排查步骤 | 常见原因与处理 |
| --- | --- | --- |
| 后端反复重启/不 healthy | `logs -f backend`；`ps` 看依赖 | ① MySQL/Redis 未就绪：`depends_on` 健康检查会等待，确认两服务 healthy；② 连接参数/密码错误：核对 `.env` 与 `DB_HOST` |
| 数据库连接失败 | `exec co-mysql mysqladmin ping`；看日志 | 卷损坏或密码不符；确认 `mysql-data` 已挂载，密码与 `.env` 一致 |
| 页面 502/打不开 | `logs web`；`curl localhost/health` | 后端未启动或 Nginx 配置错误；先恢复后端，再 `nginx -t` 校验配置 |
| 接口 403（POST 全拒） | 检查请求头是否带 `X-XSRF-TOKEN` | CSRF 令牌未随请求携带；前端读取 XSRF-TOKEN cookie 并回写头部 |
| 中文搜索无结果 | 核对 MySQL 参数 `ngram_token_size=2` | 用了非 compose 自建库；按 compose 配置重建数据库 |
| 声誉/事件不更新 | 查 outbox 表 `last_error`、`attempts` | 消费端异常或非幂等；按日志修复，死信可将 attempts 清零后重放 |
| 监控无数据 | `curl prometheus:9090/-/healthy`；看 targets | 后端 `/actuator/prometheus` 不通或网络别名错误；核对 `prometheus.yml` target `backend:8080` |
| 磁盘满 | `df -h`；`docker system df` | 镜像/日志堆积；`docker image prune -f`、清理旧卷与归档日志（先确认无需保留） |
| 内存不足/OOM | `docker stats`；看系统日志 | 容器超内存；调整 `JAVA_OPTS` 的 MaxRAMPercentage 或扩容 |
| 死信指标 >0 | Grafana 看 `co_outbox_dead_letters`，查 outbox | 事件超过最大重试；人工定位根因后清零 attempts 重放 |
| 声誉不一致 >0 | 看 `co_reputation_mismatches` 与对账日志 | 余额与流水不符；按对账结果人工冲正并排查幂等 |

**通用原则**：先看监控与日志定位，再做最小可逆操作；任何删除/回滚前先备份；处理后记录时间、原因与动作。

## 8. 备份与恢复

```bash
# 逻辑备份数据库（不影响运行）
docker exec co-mysql mysqldump -u root -p"$MYSQL_ROOT_PASSWORD" --single-transaction \
  campus_overflow > backup_$(date +%Y%m%d).sql
# 恢复
docker exec -i co-mysql mysql -u root -p"$MYSQL_ROOT_PASSWORD" campus_overflow < backup_YYYYMMDD.sql
```

- 建议定期（如每日）备份并异地保存；升级/回滚前强制备份。
- Redis AOF、Prometheus/Grafana 数据随卷持久化；备份卷可用
  `docker run --rm -v <卷>:/data -v "$PWD":/backup alpine tar czf /backup/x.tgz -C /data .`。

## 9. 升级路径与值班约定

- 自动部署依赖 CI/CD 与 GHCR；服务器需配置 `SSH_HOST/SSH_USER/SSH_KEY` 等 Secrets（见仓库根 `.github/workflows/ci.yml`）。
- 值班无法解决的问题，按 开发 → SM（陈洲）→ PO/指导教师 升级，并保留现场日志与监控截图。
- 每次事故后在回顾会复盘，必要时更新本手册版本。

> 本手册为 v1.0，覆盖启动、停止、回滚与常见故障处置；后续随系统演进补充告警规则与更多故障场景。
