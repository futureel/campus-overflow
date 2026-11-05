# 本地起后端 + 前端联调实录（任务 2 / 任务 7）

> 记录日期：2026-11-05　执行人：彭天赐（Dev B）
> 结论先说：**后端能起来了，但 Q&A（S2）和 Reputation（S3）还是 TODO 桩，所以任务 7 只能跑通一半。**
> 不是前端没写——登录、通知、搜索、提问页渲染这四条链路是真连上后端跑通的，剩下三个接口是后端抛 `UnsupportedOperationException`。

---

## 一、发现并修掉的仓库 bug（影响所有人，不只是我）

`backend/co-bootstrap/src/main/resources/application.yml` 里的 JDBC URL 写的是：

```
characterEncoding=utf8mb4
```

`utf8mb4` 是 MySQL 的**字符集名**，不是 Java 的**字符集名**。MySQL Connector/J 9.1.0 会直接用 `String.getBytes("utf8mb4")` 去取编码，于是启动就炸：

```
java.io.UnsupportedEncodingException: utf8mb4
FlywaySqlException: Unable to obtain connection from database: Unsupported character encoding 'utf8mb4'
```

结论：**任何人按原来的配置都连不上 MySQL，后端根本起不来**（不管数据库是 Docker 起的还是本机装的）。
正确写法是把参数值换成 Java 字符集名 `UTF-8`（表本身的 `utf8mb4` 由建库语句和 `charset` 决定，跟这个参数无关）：

```
...?useUnicode=true&characterEncoding=UTF-8&connectionTimeZone=UTC&...
```

改完实测：Flyway 五个目录（platform / identity / qa / reputation / discovery）全部迁移成功，后端正常监听 8080。

---

## 二、本机环境怎么搭起来的（没有装 Docker）

| 组件 | 现状 | 我的做法 |
|---|---|---|
| Java | 21.0.2（已装） | 直接用 |
| Maven | 3.9.4（已装） | `mvn -B -DskipTests package`，53.9s 出 `campus-overflow.jar`（79MB） |
| MySQL | 本机 3306 有服务，但 `root` / `campus` 的密码都不是仓库默认值，连不上 | **不动它**，另起一个独立实例：端口 `3307`、数据目录在临时目录，用 `--initialize-insecure` 初始化后通过 `--init-file` 建库建账号 |
| Redis | 没有 | 下载 portable 版 `redis-server`（5.0.14.1），`--port 6379` 直接跑，不装服务、不要管理员权限 |
| Docker Desktop | 没装（要 WSL2 + 管理员 + 重启） | 没装。`docker compose up` 的验收方式在本机不适用，改成上面这套等效环境 |

后端启动用的环境变量（都不改代码）：

```
DB_HOST=127.0.0.1  DB_PORT=3307  DB_NAME=campus_overflow
DB_USER=campus    DB_PASSWORD=campus_dev_pwd
REDIS_HOST=127.0.0.1  REDIS_PORT=6379
SERVER_PORT=8080
```

> 想跟 `docker-compose.yml` 完全一致的话，把 `DB_PORT` 改回 3306、用 compose 里的 MySQL 即可；`charset` 的 bug 修了之后两种方式都能起。

---

## 三、接口实测矩阵（curl 真实打，带 CSRF 与 Session）

前端调用链：`GET /api/v1/auth/csrf` 拿 `XSRF-TOKEN` → 写操作带 `X-XSRF-TOKEN` 头 → 会话在 HttpOnly `SESSION` Cookie 里。

| 前端页面 / 调用 | 接口 | 实测 | 判断 |
|---|---|---|---|
| 注册 | `POST /api/v1/auth/register` | 201 `{"id":1}` | ✅ 通 |
| 登录 | `POST /api/v1/auth/login` | 200，返回完整 `Me` | ✅ 通 |
| 当前用户 | `GET /api/v1/auth/me` | 200 | ✅ 通 |
| 通知列表 | `GET /api/v1/notifications` | 200 空列表 | ✅ 通 |
| 搜索（Discovery） | `GET /api/v1/search/questions?q=Spring` | 200 `{"items":[],"total":0}` | ✅ 通 |
| 问题列表 | `GET /api/v1/questions?page=1` | **500** | ❌ 后端 `QuestionQueryService.list` 是 TODO |
| 热门标签 | `GET /api/v1/tags/popular` | **500** | ❌ 同上 `popularTags` |
| 提问 | `POST /api/v1/questions` | **500** | ❌ `QuestionCommandService.post` |
| 排行榜 | `GET /api/v1/leaderboard` | **500** | ❌ `LeaderboardService.top`（Reputation S3） |
| 通知流 | `GET /api/v1/notifications/stream` | **404** | ⚠ 前端自动退回 30s 轮询，功能可用 |

后端日志里抓到的原始堆栈（这就是 500 的全部原因）：

```
UnsupportedOperationException: TODO(S2)：QuestionQueryService.list 尚未实现
UnsupportedOperationException: TODO(S2)：QuestionQueryService.popularTags 尚未实现
UnsupportedOperationException: TODO(S2)：QuestionCommandService.post 尚未实现
UnsupportedOperationException: TODO(S3)：LeaderboardService.top 尚未实现
UnsupportedOperationException: TODO(S3)：BountyService.expireDue 尚未实现   # 定时任务，每 5 分钟报一次
```

另外 `/actuator/health` 返回 503，但 `/actuator/health/readiness` 是 UP，业务接口不受影响；
503 来自健康检查里 Redis 那一项读不到 INFO（便携版 Redis 5.0.14 与 Spring Boot 3.4 的探针解析不兼容），换 compose 的 redis:7 应该就正常。

---

## 四、浏览器里真跑了一遍（Vite dev + 无头 Chrome CDP）

启动 `npm run dev`（Vite 5.4.21，监听到 `localhost:5173`，注意只绑在 IPv6 的 `::1`，用 `127.0.0.1` 打不开），
用 CDP 精确设 1280×800，在真实页面里填表单、点提交：

| 步骤 | 结果 |
|---|---|
| 打开 `/login` | 渲染「学号 / 工号」「密码」「登录」，控制台只有 2 条 401（未登录时 `me` / `unread-count` 的预期返回） |
| 填账号密码 + 点表单提交 | **跳到 `/`，顶栏变成「通知 · PTCTester · STUDENT · 退出」**，说明 Session 建起来了 |
| `/` 首页 | 渲染筛选条 + 「服务器开小差了」+ 热门标签区（问题列表 500 的降级提示正常显示，没白屏） |
| `/notifications` | 「暂时没有通知。」+「全部标记为已读」，200 |
| `/ask` | 渲染出 Markdown 编辑器（`.md-editor` 存在）、`+ 添加悬赏` 按钮，说明任务 10 的组件在真实路由里正常挂载 |

补充：控制台里那两条 `404` 就是 `/api/v1/notifications/stream`（后端还没这个端点），
正好验证了 `useNotificationStream` 的降级路径——SSE 失败后自动落回轮询，页面不报错、功能不中断。

---

## 五、任务 2 / 7 的准确结论

- **任务 2（跑通后端环境）**：环境能跑通（后端起来、Flyway 建表、注册登录可用）。**不能**按原清单的「Docker Desktop 里看到 co-mysql / co-redis 容器」验收，因为本机没装 Docker，用的是等效的便携环境。
- **任务 7（前端对接后端）**：6 个页面里，登录页、通知页、提问页、首页框架都已真连后端；**问题列表 / 详情 / 排行榜 / 发布问题** 四个点卡在后端未实现，需要袁有为把 S2、S3 的 TODO 补掉，前端不需要再改。
- 需要团队决策的两件事：① `characterEncoding` 这个 bug 要不要我直接提 PR（已改好并验证）；② 谁负责补 S2/S3 的桩（清单里写的是袁有为）。
