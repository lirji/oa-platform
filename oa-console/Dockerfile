# ---- build ----
FROM node:20-alpine AS build
WORKDIR /app
# 与本仓库生成 lockfile 的 pnpm 版本保持一致，避免 Corepack 自动选到要求 Node 22 的 pnpm 11。
RUN corepack enable && corepack prepare pnpm@9.15.9 --activate
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
# Vite 环境变量在构建期写入静态产物；compose 通过 build args 统一传入。
ARG VITE_API_BASE_URL=
ARG FRONTEND_OIDC_ENABLED=false
ARG VITE_CASDOOR_AUTHORITY=http://localhost:8000
ARG VITE_CASDOOR_CLIENT_ID=
ARG VITE_OIDC_SCOPE="openid profile email offline_access"
ARG VITE_WS_PATH=/ws
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL} \
    VITE_CASDOOR_AUTHORITY=${VITE_CASDOOR_AUTHORITY} \
    VITE_CASDOOR_CLIENT_ID=${VITE_CASDOOR_CLIENT_ID} \
    VITE_OIDC_SCOPE=${VITE_OIDC_SCOPE} \
    VITE_WS_PATH=${VITE_WS_PATH}
# gen:perm 需要读取仓库根的后端迁移，Docker 构建上下文刻意只给 oa-console，
# 所以它在宿主/CI 先跑；镜像内仍检查自包含的 OpenAPI 快照。
RUN export VITE_AUTH_ENABLED="${FRONTEND_OIDC_ENABLED}" \
 && pnpm gen:api:check && pnpm build && pnpm size

# ---- serve ----
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY proxy_params /etc/nginx/proxy_params
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8404
HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=5 \
  CMD wget -q -O /dev/null http://127.0.0.1:8404/healthz || exit 1
CMD ["nginx", "-g", "daemon off;"]
