# 格物前端：vben monorepo 构建 web-antd → nginx 托管 + API 反代
FROM docker.m.daocloud.io/library/node:22-alpine AS builder
WORKDIR /app

# CI=true 跳过 lefthook；npmmirror 加速依赖安装
ENV CI=true \
    TZ=Asia/Shanghai \
    NODE_OPTIONS=--max-old-space-size=8192 \
    COREPACK_NPM_REGISTRY=https://registry.npmmirror.com

COPY . /app
RUN corepack enable
RUN --mount=type=cache,id=pnpm,target=/pnpm/store pnpm install --frozen-lockfile

# 构建时注入 API 地址（走容器内 nginx 反代，同源）
ENV VITE_GLOB_API_URL=/api/v1
RUN pnpm run build --filter=@vben/web-antd

FROM docker.m.daocloud.io/library/nginx:stable-alpine
COPY nginx-frontend.conf /etc/nginx/nginx.conf
COPY --from=builder /app/apps/web-antd/dist/ /usr/share/nginx/html/
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
