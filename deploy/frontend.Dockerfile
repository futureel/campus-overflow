# 前端多阶段构建：Node 编译静态资源 → Nginx 托管 + 反向代理
# ---------- 阶段 1：构建 ----------
FROM node:20-alpine AS build
WORKDIR /src
# 先拷贝依赖清单利用缓存（main 基线无 package-lock，使用 npm install）
COPY frontend/package.json ./
RUN npm install --no-audit --no-fund
COPY frontend/ ./
# 构建期禁用遥测，输出到 dist
RUN npm run build

# ---------- 阶段 2：Nginx 运行 ----------
FROM nginx:1.27-alpine
# 自定义站点配置（SPA 回退 + /api 反代 + 安全头 + 缓存策略）
COPY deploy/nginx.conf /etc/nginx/conf.d/default.conf
# 静态资源
COPY --from=build /src/dist /usr/share/nginx/html
EXPOSE 80
# 容器健康检查：nginx 自身存活即可（/ 返回 index.html）
HEALTHCHECK --interval=10s --timeout=3s --retries=10 \
  CMD wget -qO- http://localhost/ >/dev/null 2>&1 || exit 1
