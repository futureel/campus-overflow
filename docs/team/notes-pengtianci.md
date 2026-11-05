# 彭天赐 · 前端代码阅读笔记（Sprint 2 · 任务 6）

> 结论先行：前端**没有用 axios**，用的是浏览器原生 `fetch`；登录态**不存 localStorage**，而是服务端会话（HttpOnly Cookie）；
> 路由共 **6 个页面**（任务清单里写的 5 个，实际多一个「通知」页）。

## 1. 前端是怎么调后端接口的

- 依赖清单里只有 `react` / `react-dom` / `react-router-dom`，**没有 axios**，所有请求走 `fetch`。
- 所有网络请求集中在一个文件：`src/api.ts`
  - 核心是最底层的 `request<T>(method, path, body)`，其余接口都是它的薄封装（`get` / `api.xxx`）。
  - 请求路径统一加前缀 `/api`，即 `fetch('/api' + path)`。
  - **跨域怎么解决**：开发期由 `vite.config.ts` 的 `server.proxy` 把 `/api` 代理到 `http://localhost:8080`，前后端同源，所以 Cookie 与 CSRF 都正常工作；生产期由 Nginx 反代（`deploy/nginx.conf`）。
  - **Cookie 必须带上**：每个请求都写了 `credentials: 'include'`，否则浏览器不会带会话 Cookie。
  - **写操作要带 CSRF token**：非 GET 请求会先从 `XSRF-TOKEN` Cookie 取令牌放进 `X-XSRF-TOKEN` 请求头；如果 Cookie 里还没有，就先 `GET /api/v1/auth/csrf` 拿一次再重发（双提交校验）。
  - **错误统一处理**：后端返回 RFC 9457 Problem Details，前端把它转成 `ApiError`（带 `status` + 业务码 `code`），页面里 `catch` 之后显示 `e.message`。
  - 特殊约定：`204 No Content` 直接返回 `undefined`（例如退出登录、删除问题、采纳回答）。

## 2. 登录态是怎么保存的

**不是 localStorage，也不是前端自己存 token。**

- `src/auth.tsx` 用 React Context（`AuthProvider` / `useAuth`）在**内存**里放当前用户对象 `me`。
- 真正的会话在**后端**：登录成功后后端下发 HttpOnly 会话 Cookie，浏览器自动携带；前端读不到也改不了。
- 应用启动时（`AuthProvider` 的 `useEffect`）先调一次 `api.csrf()`，再调 `api.me()`：
  - 成功 → `me` 有值，说明已登录；
  - 401 → `me = null`，属正常未登录状态（不是报错）。
- 所以**刷新页面登录态不会丢**（Cookie 还在），但 `me` 需要重新请求一次才知道。
- `RequireAuth` 组件负责路由守卫：`loading` 时显示「加载中…」，未登录就 `<Navigate to="/login" replace />`。
- 退出登录 = 调 `POST /api/v1/auth/logout`，后端销毁会话，前端把 `me` 置空。

## 3. 页面分别是干什么的

| 路由 | 文件 | 干什么 |
|---|---|---|
| `/` | `pages/QuestionListPage.tsx` | 问题列表 + 搜索筛选。有关键词/标签/排序/仅看未回答时调 `api.search()`，否则调 `api.questions()`；右侧栏显示热门标签，点标签即筛选 |
| `/questions/:id` | `pages/QuestionDetailPage.tsx` | 问题详情：正文、投票（`api.vote`）、回答列表、采纳（`api.accept`）、教师认证（`api.endorse`）、评论（`api.comment`）、悬赏（`api.bounty`）、右侧相关问题（`api.related`）；正文用 `dangerouslySetInnerHTML` 渲染后端净化过的 HTML |
| `/ask` | `pages/AskPage.tsx` | 提问页（`RequireAuth`）：标题 10–150 字、正文 Markdown ≥20 字、标签 1–5 个、可选所属课程；提交后跳到自己刚发的问题详情 |
| `/login` | `pages/LoginPage.tsx` | 登录 / 注册两个 Tab。注册成功会自动接着登录，然后跳首页 |
| `/leaderboard` | `pages/LeaderboardPage.tsx` | 声誉排行榜，周期切换：本周 / 本月 / 本学期 / 总榜（`period` 参数） |
| `/notifications` | `pages/NotificationsPage.tsx` | 站内通知列表（`RequireAuth`），有「全部标记为已读」；顶栏还会每 30 秒轮询 `unread-count` 显示未读数气泡 |

## 4. 从打开页面到拿到后端数据的完整流程

1. 浏览器访问 `http://localhost:5173`，Vite 返回 `index.html`，加载 `src/main.tsx`。
2. `main.tsx` 挂载 `App`，`App` 里先是 `BrowserRouter`，再是 `AuthProvider`。
3. `AuthProvider` 启动：`api.csrf()` 拿 CSRF → `api.me()` 判断登录态 → 写入 Context 的 `me`。顶栏 `Header` 依据 `me` 决定显示「登录/注册」还是用户名，并对未读数发起轮询。
4. 路由匹配到某个页面组件（例如 `/` → `QuestionListPage`）。
5. 页面组件在 `useEffect` 里调 `api.xxx()`。
6. `api.xxx` → `request()` → `fetch('/api/...')` → Vite dev server 代理到 `localhost:8080` → 后端 Controller → 应用层 → 领域层 → 数据库，返回 JSON。
7. `fetch` 拿到响应：非 2xx 抛 `ApiError`；2xx 解析 JSON 返回。
8. 页面 `setData(...)` 更新 state → React 重新渲染列表/详情。
9. 出错时页面把错误信息渲染到 `<p className="error">`，不会白屏。

## 5. 现在前端的真实状态（2026-11-05 核对）

- `npm install` 与 `npm run dev` 均可跑通，`http://localhost:5173` 正常监听。
- **前端代码本身基本写完**：`api.ts` 已覆盖认证、问题、回答、投票、评论、搜索、相关推荐、排行榜、通知、悬赏等全部接口；6 个页面均已实现。
- **卡点在后端**：`backend` 里还有 214 处 `TODO(Sx)`（Q&A 72 / Reputation 78 / Discovery 64），大量方法仍抛 `UnsupportedOperationException`，所以后端接口目前多数返回 500，前端页面会显示错误信息而不是数据。
- 结论：Sprint 2 的「前端对接」不是前端没写，而是**等后端把 TODO 实现完**才能真正联调。
