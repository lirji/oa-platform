#!/usr/bin/env bash
# Phase 4：真实 Casdoor token、四服务 JWT 矩阵、浏览器授权码/PKCE/续期、一次性 WS ticket。
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE="$ROOT/deploy/docker-compose.yml"
CASDOOR="${CASDOOR_URL:-http://localhost:8000}"
CLIENT_ID="${OA_CASDOOR_CLIENT_ID:-oa-platform-local}"
CLIENT_SECRET="${OA_CASDOOR_CLIENT_SECRET:-oa-platform-local-secret-2026}"
ADMIN_USER="${CASDOOR_ADMIN:-admin}"
ADMIN_PASSWORD="${CASDOOR_ADMIN_PW:-123}"
APP_PORT="${OA_PHASE4_APP_PORT:-18400}"
CONSOLE_PORT="${OA_CONSOLE_PORT:-8404}"
TAG="${OA_IMAGE_TAG:-local}"
PASS=0; FAIL=0
ok(){ echo "  ✅ $1"; PASS=$((PASS+1)); }
bad(){ echo "  ❌ $1"; FAIL=$((FAIL+1)); }
step(){ echo; echo "── $1"; }
http(){ curl -sS -o /dev/null -w '%{http_code}' "$@" 2>/dev/null || echo 000; }
jwt_payload(){
  local value="${1#*.}"; value="${value%%.*}"; value="${value//-/+}"; value="${value//_//}"
  case $(( ${#value} % 4 )) in 2) value="${value}==";; 3) value="${value}=";; esac
  printf '%s' "$value" | base64 -d 2>/dev/null || printf '%s' "$value" | base64 -D 2>/dev/null
}

command -v jq >/dev/null || { echo "需要 jq" >&2; exit 1; }
command -v curl >/dev/null || { echo "需要 curl" >&2; exit 1; }

step "1. Casdoor 应用与真实 token"
bash "$ROOT/deploy/scripts/provision-oa-casdoor.sh" || exit 1
TOKEN=$(curl -fsS -X POST "$CASDOOR/api/login/oauth/access_token" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'grant_type=password' --data-urlencode "username=$ADMIN_USER" \
  --data-urlencode "password=$ADMIN_PASSWORD" --data-urlencode "client_id=$CLIENT_ID" \
  --data-urlencode "client_secret=$CLIENT_SECRET" \
  --data-urlencode 'scope=openid profile email offline_access' | jq -r '.access_token // empty')
[ -n "$TOKEN" ] || { bad "Casdoor 未签发 OA access token"; exit 1; }
CLAIMS=$(jwt_payload "$TOKEN")
SUB=$(printf '%s' "$CLAIMS" | jq -r '.sub // empty')
ISSUER=$(printf '%s' "$CLAIMS" | jq -r '.iss // empty')
AUD_OK=$(printf '%s' "$CLAIMS" | jq -r --arg aud "$CLIENT_ID" '(.aud // []) | index($aud) != null')
[ -n "$SUB" ] && [ "$ISSUER" = "$CASDOOR" ] && [ "$AUD_OK" = "true" ] \
  && ok "真实 token 的 iss/aud/sub 契约正确（${#TOKEN} bytes）" || { bad "token claim 契约错误"; exit 1; }

BUILTIN_ID="${CASDOOR_BUILTIN_CLIENT_ID:-ea46d9a8033b0be2d8ed}"
BUILTIN_SECRET=$(docker exec authz-postgres psql -U authz -d spicedb -tAc \
  "select client_secret from application where client_id='${BUILTIN_ID}'" | tr -d '[:space:]')
WRONG_AUD_TOKEN=$(curl -fsS -X POST "$CASDOOR/api/login/oauth/access_token" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'grant_type=password' --data-urlencode "username=$ADMIN_USER" \
  --data-urlencode "password=$ADMIN_PASSWORD" --data-urlencode "client_id=$BUILTIN_ID" \
  --data-urlencode "client_secret=$BUILTIN_SECRET" --data-urlencode 'scope=openid' | jq -r '.access_token // empty')
[ -n "$WRONG_AUD_TOKEN" ] || { bad "无法取得错误 audience 对照 token"; exit 1; }

step "2. 从当前源码构建并启动 JWT 四服务"
OA_BUILD_CONSOLE=false OA_BUILD_MOBILE=false bash "$ROOT/deploy/build-images.sh" \
  && ok "当前源码后端镜像完成" || { bad "后端镜像构建失败"; exit 1; }
OA_APP_PORT="$APP_PORT" OA_SECURITY_MODE=JWT OA_JWT_JWKS=http://host.docker.internal:8000/.well-known/jwks \
OA_JWT_ISSUER="$ISSUER" OA_JWT_AUDIENCE="$CLIENT_ID" OA_BOOTSTRAP_ADMIN="$SUB" OA_WORKFLOW_MODE=LOCAL \
  docker compose -p oa-platform -f "$COMPOSE" --profile apps up -d oa-app oa-notify oa-file oa-job || exit 1
for i in $(seq 1 60); do
  [ "$(http "http://127.0.0.1:${APP_PORT}/actuator/health")" = "200" ] && \
  [ "$(http http://127.0.0.1:8401/api/v1/notify/ping)" = "200" ] && \
  [ "$(http http://127.0.0.1:8402/api/v1/file/ping)" = "200" ] && \
  [ "$(http http://127.0.0.1:8403/api/v1/job/ping)" = "200" ] && break
  sleep 2
done
if [ "$(http "http://127.0.0.1:${APP_PORT}/actuator/health")" = "200" ] && \
   [ "$(http http://127.0.0.1:8401/api/v1/notify/ping)" = "200" ] && \
   [ "$(http http://127.0.0.1:8402/api/v1/file/ping)" = "200" ] && \
   [ "$(http http://127.0.0.1:8403/api/v1/job/ping)" = "200" ]; then
  ok "四服务 JWT profile 健康"
else
  bad "JWT 服务未就绪"
  docker ps -a --filter name=oa- --format '{{.Names}} {{.Status}}'
  exit 1
fi

step "3. 四服务认证矩阵"
protected=(
  "http://127.0.0.1:${APP_PORT}/api/v1/me/permissions"
  "http://127.0.0.1:8401/api/v1/notify/messages/unread-count"
  "http://127.0.0.1:8402/api/v1/file/mine"
  "http://127.0.0.1:8403/api/v1/job/runs"
)
for endpoint in "${protected[@]}"; do
  [ "$(http "$endpoint")" = "401" ] || bad "无 token 未被 401：$endpoint"
  [ "$(http -H 'X-OA-User: seed-user-1' "$endpoint")" = "401" ] || bad "JWT 模式错误信任 X-OA-User：$endpoint"
  [ "$(http -H "Authorization: Bearer $TOKEN" "$endpoint")" = "200" ] || bad "合法 token 未通过：$endpoint"
done
[ "$FAIL" -eq 0 ] && ok "四服务无 token / DEV header / 合法 token 矩阵通过"

LAST=${TOKEN: -1}; [ "$LAST" = "A" ] && BAD_TOKEN="${TOKEN%?}B" || BAD_TOKEN="${TOKEN%?}A"
[ "$(http -H "Authorization: Bearer $BAD_TOKEN" "http://127.0.0.1:${APP_PORT}/api/v1/me/permissions")" = "401" ] \
  && ok "篡改签名 token 被拒" || bad "篡改签名 token 未被拒"
[ "$(http -H "Authorization: Bearer $WRONG_AUD_TOKEN" "http://127.0.0.1:${APP_PORT}/api/v1/me/permissions")" = "401" ] \
  && ok "错误 audience token 被拒" || bad "错误 audience token 未被拒"
[ "$(http -H "Authorization: Bearer $TOKEN" "http://127.0.0.1:${APP_PORT}/actuator/health")" = "200" ] \
  && ok "8KB+ Casdoor token 未触发 Tomcat 400" || bad "合法大 token header 失败"
[ "$FAIL" -eq 0 ] || exit 1

step "4. JWT 控制台镜像与一次性 WebSocket ticket"
docker build -q -f "$ROOT/oa-console/Dockerfile" \
  --build-arg FRONTEND_OIDC_ENABLED=true \
  --build-arg VITE_CASDOOR_AUTHORITY="$CASDOOR" \
  --build-arg VITE_CASDOOR_CLIENT_ID="$CLIENT_ID" \
  --build-arg 'VITE_OIDC_SCOPE=openid profile email offline_access' \
  -t "oa-platform/oa-console:${TAG}" "$ROOT/oa-console" >/dev/null || exit 1
OA_APP_PORT="$APP_PORT" OA_CONSOLE_PORT="$CONSOLE_PORT" OA_SECURITY_MODE=JWT \
OA_JWT_JWKS=http://host.docker.internal:8000/.well-known/jwks OA_JWT_ISSUER="$ISSUER" \
OA_JWT_AUDIENCE="$CLIENT_ID" OA_BOOTSTRAP_ADMIN="$SUB" OA_WORKFLOW_MODE=LOCAL \
  docker compose -p oa-platform -f "$COMPOSE" --profile apps up -d oa-console || exit 1
# 即使控制台镜像未变，也要覆盖“后端滚动重建、旧 nginx 仍缓存旧 IP”的发布场景。
OA_APP_PORT="$APP_PORT" OA_CONSOLE_PORT="$CONSOLE_PORT" OA_SECURITY_MODE=JWT \
OA_JWT_JWKS=http://host.docker.internal:8000/.well-known/jwks OA_JWT_ISSUER="$ISSUER" \
OA_JWT_AUDIENCE="$CLIENT_ID" docker compose -p oa-platform -f "$COMPOSE" --profile apps restart oa-console >/dev/null || exit 1
for i in $(seq 1 45); do [ "$(http "http://127.0.0.1:${CONSOLE_PORT}/healthz")" = "204" ] && break; sleep 1; done
[ "$(http "http://127.0.0.1:${CONSOLE_PORT}/healthz")" = "204" ] \
  && ok "JWT 控制台镜像健康" || { bad "JWT 控制台未就绪"; exit 1; }

TICKET=$(curl -fsS -X POST -H "Authorization: Bearer $TOKEN" \
  "http://127.0.0.1:${CONSOLE_PORT}/api/v1/notify/ws-ticket" | jq -r '.data.ticket // empty')
[ "$TICKET" != "" ] && [ "${#TICKET}" -eq 43 ] || { bad "未取得 256-bit WS ticket"; exit 1; }
java "$ROOT/deploy/scripts/WsProbe.java" "ws://127.0.0.1:${CONSOLE_PORT}/ws" "ticket=$TICKET" 0 2 \
  "http://127.0.0.1:${CONSOLE_PORT}" >/dev/null 2>&1 \
  && ok "浏览器等价 Origin + 一次性 ticket 握手成功" || { bad "ticket 握手失败"; exit 1; }
if java "$ROOT/deploy/scripts/WsProbe.java" "ws://127.0.0.1:${CONSOLE_PORT}/ws" "ticket=$TICKET" 0 1 \
  "http://127.0.0.1:${CONSOLE_PORT}" >/dev/null 2>&1; then
  bad "ticket 可被重放"
else
  ok "ticket 取用即删，重放被拒"
fi
if java "$ROOT/deploy/scripts/WsProbe.java" "ws://127.0.0.1:${CONSOLE_PORT}/ws" "$SUB" 0 1 \
  "http://127.0.0.1:${CONSOLE_PORT}" >/dev/null 2>&1; then
  bad "JWT WebSocket 错误接受 userId query"
else
  ok "JWT WebSocket 拒绝 DEV userId query"
fi
docker logs oa-console 2>&1 | grep -q 'GET /ws?ticket=' \
  && bad "Nginx access log 泄露 ticket" || ok "Nginx 不记录 WS ticket 查询串"
[ "$FAIL" -eq 0 ] || exit 1

step "5. 真实浏览器授权码 + PKCE + 回调 + refresh + WS"
(cd "$ROOT/oa-console" && PLAYWRIGHT_BASE_URL="http://127.0.0.1:${CONSOLE_PORT}" OA_JWT_E2E=true \
  pnpm exec playwright test e2e/jwt-auth.spec.ts) \
  && ok "真实 Casdoor 浏览器闭环通过" || { bad "浏览器 OIDC 闭环失败"; exit 1; }

echo; echo "════════════════════════════════"
echo "  Phase 4 JWT/WS：通过 ${PASS} / 失败 ${FAIL}"
echo "════════════════════════════════"
[ "$FAIL" -eq 0 ]
