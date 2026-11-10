# 常见报错 FAQ（环境与开发问题排查）

> 维护人：陈洲（SM）　来源：Sprint 1 回顾会改进项 S1，汇总各 Sprint 站会阻碍的标准解法。
> 遇到问题先查本文件；新问题解决后补进来。

## 1. 环境与工具类

### Q1 启动后端报连库失败 / Communications link failure
中间件容器没起或未就绪。
```
docker compose -f deploy/docker-compose.yml up -d mysql redis
docker compose -f deploy/docker-compose.yml ps        # 确认 STATUS 为 healthy
```

### Q2 Maven 依赖下载超时 / 插件拉不下来
配置国内镜像。在 `~/.m2/settings.xml` 加入阿里云镜像，或 IDE 里确认 Maven 指向该配置；
公司/校园网络可切换网络后重试 `mvn -U clean install`。

### Q3 前端 vite 启动报 Port 5173 is already in use
端口被占用。
```
npx kill-port 5173
# 或换端口启动
npm run dev -- --port 5174
```

### Q4 看不懂 ArchUnit / ArchitectureTest 报红
先确认是规则失败还是环境问题：单测能跑通而仅架构测试失败，说明是代码违反架构约束，
按报错定位（如领域层引入了框架注解、跨上下文直接引用实现类）。规则含义见 ci.yml 注释与 ADR。

## 2. 前后端联调类

### Q5 POST/PUT/DELETE 全部返回 403（CSRF）
前端未携带 CSRF 令牌。安全链通过 `XSRF-TOKEN` cookie 下发令牌，
前端读取该 cookie 后在写请求头加 `X-XSRF-TOKEN`；确认 CsrfCookieFilter 已生效。

### Q6 采纳/投票后声誉或下游没有变化
走事件链路排查：
1. 查 `outbox_event` 表是否有对应事件、`published_at` 是否为空、看 `last_error`；
2. `last_error` 有值说明订阅者处理失败，按异常修复（常见为幂等表/迁移未建、字段不匹配）；
3. 修复后中继会自动重试，死信（attempts 达上限）人工将 attempts 清零重放。

## 3. 代码规范类

### Q7 git commit 被 pre-commit 钩子拦截
按终端输出修复：ESLint 错误先 `npm run lint:fix`，格式问题 `npm run format`，
重新暂存后再提交。不要默认用 `--no-verify` 跳过。

### Q8 prettier --check 报一堆文件不符合
运行 `npm run format` 统一格式化后提交；Windows 上仓库统一使用 LF（见 .prettierrc 的 endOfLine）。

### Q9 Markdown 正文的长度校验和 XSS 过滤放哪
长度/业务规则放领域层（MarkdownBody 值对象），HTML 净化（OWASP sanitizer）放基础设施/应用层，
领域层不依赖具体净化框架（ADR-008）。
