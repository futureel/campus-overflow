# 彭天赐 · 前端验收记录（任务 1 / 10 / 11）

> 记录日期：2026-11-05
> 环境：Windows，Node v22.23.2，npm 10，Chrome（无头 + CDP 精确视口）
> 说明：后端未启动（Redis 缺失 + Q&A/Reputation 仍有 TODO），凡涉及 `/api` 的请求都会 502，
> 因此**接口联调（任务 7）不在本记录范围内**，本文只给前端本身可独立验证的部分。

---

## 一、任务 1：前端环境 + 浏览器实际渲染

| 检查项 | 方法 | 结果 |
|---|---|---|
| 依赖安装 | `npm install` | 71 个包完成 |
| 生产构建 | `npm run build`（= `tsc -b && vite build`） | 通过，47 modules，577~593ms |
| 构建产物 | `dist/` | `index.html` 0.42 kB、`index-*.css` 6.97 kB(gzip 2.00)、`index-*.js` 184.22 kB(gzip 60.55) |
| 登录页渲染 | 无头 Chrome + CDP，`/login` | `innerWidth=390`、`scrollWidth=390`、无横向溢出；正文含「学号 / 工号」「密码」「登录」 |
| 控制台红色报错 | CDP 监听 `Runtime.consoleAPICalled` / `Log.entryAdded` / `exceptions` | `/login` **0 条** error/warning |
| 首页渲染 | CDP，`/` | 渲染出「最新 / 最近活跃 / 得分最高 / 相关度 / 仅看未回答 / 热门标签」筛选区 |
| 首页控制台 | 同上 | 1 条 `502 (Bad Gateway)`，来源是 `/api/v1/questions`，**后端没起**，不是前端问题 |

修复项：登录表单原本是裸 `<input>`，Chrome 报 `Password field is not contained in a form`。
已改为 `<form onSubmit>` 包裹，并补 `autocomplete="username" / "current-password"`，
回车可提交、密码管理器可识别；改完 `/login` 控制台已无任何提示。

---

## 二、任务 10：前端四项增强

### 1. Markdown 实时预览
- `src/markdown.ts`：自写极简渲染器。**先整体转义 HTML 再受控替换**，所以不需要 DOMPurify 也不会 XSS；
  链接只放行 `https?://` 并加 `rel="noopener noreferrer"`；围栏代码块的语言标识只保留 `[a-z0-9-]`。
- `src/components/MarkdownEditor.tsx`：桌面左右分栏实时预览；≤720px 收成「编辑 / 预览」两个页签。
- 接入位置：提问页正文、问题详情页「写回答」。
- 用例 12/12 通过（`node --experimental-strip-types` 直接跑渲染器）：

| 用例 | 期望 | 结果 |
|---|---|---|
| `<script>alert(1)</script>` | 被转义，不产生标签 | PASS |
| `**重点**` | `<strong>重点</strong>` | PASS |
| `` `npm test` `` | `<code>` | PASS |
| 代码块内 `a < b` | 转义为 `&lt;` | PASS |
| `[点我](javascript:alert(1))` | 不成链 | PASS |
| `[文档](https://example.com/x)` | 成链 + `noopener noreferrer` | PASS |
| `- 甲 / - 乙` | 2 个 `<li>` | PASS |
| `1. 甲 / 2. 乙` | 2 个 `<li>` | PASS |
| `> 引用` | `<blockquote>` | PASS |
| `## 二级` | `<h2>` | PASS |
| 空白输入 | 返回空串 | PASS |
| 单换行 | `<br/>` | PASS |

### 2. 悬赏弹窗
- `src/components/BountyDialog.tsx`：积分 10–500（步长 10 的滑杆）、有效期 1/3/7/14/30 天、超范围禁用确认；
  点遮罩取消、`stopPropagation` 防穿透。
- 提问页：`+ 添加悬赏` 打开弹窗，已选可取消；提交时**先发问题再设悬赏**，
  悬赏失败**不回滚、不重复提问**，会停留在「问题已发布」页并给出 warning + 直达链接。
- 问题详情页：原来的 `window.prompt()` 换成同一个弹窗（手机端不再被系统弹窗打断，且能校验范围）。

### 3. SSE 实时通知
- `src/useNotificationStream.ts`：优先 `new EventSource('/api/v1/notifications/stream', { withCredentials: true })`，
  `onopen` 停轮询；监听 `notification` 事件与 `onmessage` 弹提醒并刷新未读数；
  `onerror` 关掉 SSE 退回 30 秒轮询，避免重连风暴。
- `src/components/Toasts.tsx`：右上角提醒，6 秒自动消失、可点 × 关、`aria-live="polite"`。
- `App.tsx`：删掉原来 Header 里的 30s `setInterval`，统一走这个 hook。
- **现状说明**：后端目前没有 `/api/v1/notifications/stream` 端点，实测会立即 `onerror`，
  也就是现在走的是 30 秒轮询兜底，功能可用；等后端补上 SSE 端点后前端不需要再改。

### 4. 移动端适配
- `styles.css` 增加 ≤860px（单列）、≤720px（Markdown 单栏 + 页签）、≤640px（顶部导航折行、问题列表竖排、按钮加高、字号 16px 防 iOS 聚焦放大、toast 移到底部）三档。
- 实测 390×844：`innerWidth=390`、`scrollWidth=390`、无横向溢出。

---

## 三、任务 11：Nginx 托管验证

方法：下载 nginx 1.27.2（Windows 版），把 `deploy/nginx.conf` 改写成本机可跑的形式
（`listen 18080`、`root` 指向 `frontend/dist`、`backend:8080` → `127.0.0.1:8080`），
先 `nginx -t` 再真启动，用 `curl.exe` 打状态码。

| 请求 | 期望 | 实测 |
|---|---|---|
| `nginx -t` | 配置语法 ok | `syntax is ok` / `test is successful` |
| `/` | 200 + index.html | 200 `text/html` 430B `hasRoot` |
| `/questions/3`（前端深链接） | 200 + 回退 index.html | 200 `text/html` 430B `hasRoot` |
| `/notifications`（前端深链接） | 200 + 回退 index.html | 200 `text/html` 430B `hasRoot` |
| `/assets/index-*.js` | 200 + `application/javascript` | 200 `application/javascript` 184946B |
| `/assets/not-exist.js` | 404（不能把 HTML 当 js 返回） | 404 |
| `/api/v1/notifications/stream` | 转发到后端（后端起时为事件流） | 502（后端未启动，说明 location 命中并已反代，不是 404） |

对 `deploy/nginx.conf` 的两处补充（原文件只有 `/` 的 SPA 回退和 `/api/` 反代）：

1. `location /assets/ { try_files $uri =404; }`
   不加的话，缺失的 js/css 会被回退成 index.html 并返回 200，浏览器把 HTML 当脚本执行，报错很难查。
2. `location = /api/v1/notifications/stream`：`proxy_http_version 1.1` + `Connection ""` +
   `proxy_buffering off` + `proxy_cache off` + `proxy_read_timeout 3600s`。
   SSE 如果不关缓冲，事件会被 nginx 攒住不下发，前端就永远收不到实时通知。

---

## 四、仍未验证 / 阻塞项（如实记录）

- 任务 7 接口联调：后端 8080 起不来（`spring.session.redis.namespace=co:session` 需要 Redis，
  本机 6379 无监听；Q&A 72 处、Reputation 78 处 TODO 未实现，即使起来也会 500）。
- 真机手机：只在 Chrome 的 390×844 视口里验过，没有在真实 iPhone/Android 上点过。
- 容器化部署：`deploy/frontend.Dockerfile` 未构建（未装 Docker），本机 nginx 验证是等效验证。
