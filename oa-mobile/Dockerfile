FROM node:20-alpine AS build
WORKDIR /app
RUN corepack enable && corepack prepare pnpm@9.15.9 --activate
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
ARG VITE_API_BASE_URL=
ARG FRONTEND_OIDC_ENABLED=false
ARG VITE_CASDOOR_AUTHORITY=http://localhost:8000
ARG VITE_CASDOOR_CLIENT_ID=
ARG VITE_OIDC_SCOPE="openid profile email offline_access"
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL} \
    VITE_CASDOOR_AUTHORITY=${VITE_CASDOOR_AUTHORITY} \
    VITE_CASDOOR_CLIENT_ID=${VITE_CASDOOR_CLIENT_ID} \
    VITE_OIDC_SCOPE=${VITE_OIDC_SCOPE}
RUN export VITE_AUTH_ENABLED="${FRONTEND_OIDC_ENABLED}" \
 && pnpm test && pnpm build && pnpm size

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY proxy_params /etc/nginx/proxy_params
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8405
HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=5 \
  CMD wget -q -O /dev/null http://127.0.0.1:8405/healthz || exit 1
CMD ["nginx", "-g", "daemon off;"]
