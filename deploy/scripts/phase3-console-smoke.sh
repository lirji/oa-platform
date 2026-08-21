#!/usr/bin/env bash
# Phase 3 PC 控制台发布前关卡：契约、单测、生产构建、真实浏览器、镜像与 nginx 四上游代理。
#
# 默认复用现有 3000/10000 夹具，不会清库。若环境没有夹具，请显式执行：
#   OA_PHASE3_RESEED=true bash deploy/scripts/phase3-console-smoke.sh
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CONSOLE="$ROOT/oa-console"
COMPOSE="$ROOT/deploy/docker-compose.yml"

set -a
[ -f "$ROOT/deploy/.env" ] && . "$ROOT/deploy/.env"
set +a

TAG="${OA_IMAGE_TAG:-local}"
APP_PORT="${OA_PHASE3_APP_PORT:-18400}"
CONSOLE_PORT="${OA_CONSOLE_PORT:-8404}"
PASS=0; FAIL=0
ok(){ echo "  ✅ $1"; PASS=$((PASS+1)); }
bad(){ echo "  ❌ $1"; FAIL=$((FAIL+1)); }
step(){ echo; echo "── $1"; }

step "1. 静态门禁"
(cd "$CONSOLE" && pnpm gen:perm --check && pnpm gen:api:check && pnpm test && pnpm build && pnpm size) \
  && ok "契约、前端单测、构建与首屏预算通过" || { bad "静态门禁失败"; exit 1; }
docker compose -p oa-platform -f "$COMPOSE" --profile apps config -q \
  && ok "compose 配置有效" || { bad "compose 配置无效"; exit 1; }

step "2. 后端与万人夹具"
OA_BUILD_CONSOLE=false OA_BUILD_MOBILE=false bash "$ROOT/deploy/build-images.sh" \
  && ok "当前源码的四个后端镜像构建成功" || { bad "后端镜像构建失败"; exit 1; }
OA_APP_PORT="$APP_PORT" OA_SECURITY_MODE=DEV OA_WORKFLOW_MODE=LOCAL OA_ORG_SEED_ENABLED=true \
  docker compose -p oa-platform -f "$COMPOSE" --profile apps up -d oa-app oa-notify oa-file oa-job
for i in $(seq 1 45); do curl -sf "http://127.0.0.1:${APP_PORT}/api/v1/system/ping" >/dev/null && break; sleep 2; done
curl -sf "http://127.0.0.1:${APP_PORT}/api/v1/system/ping" >/dev/null \
  && ok "容器后端就绪（host :${APP_PORT}，避开 IDE :8400）" || { bad "容器后端未就绪"; exit 1; }

if [ "${OA_PHASE3_RESEED:-false}" = "true" ]; then
  OA_BASE="http://127.0.0.1:${APP_PORT}/api/v1" bash "$ROOT/deploy/scripts/seed-console-fixture.sh" \
    && ok "显式重建 3000/10000 夹具" || { bad "夹具重建失败"; exit 1; }
fi
EMP=$(docker exec oa-postgres psql -U "${OA_PG_USER:-oa}" -d "${OA_PG_DB:-oa}" -tAc \
  "select count(*) from oa_org.employee where status='ACTIVE'" 2>/dev/null | tr -d '[:space:]')
[ "$EMP" = "10000" ] && ok "万人夹具就绪" || {
  bad "当前员工数 ${EMP:-0}，请用 OA_PHASE3_RESEED=true 显式重建"; exit 1;
}

step "3. 真实浏览器 E2E"
(cd "$CONSOLE" && VITE_API_TARGET="http://127.0.0.1:${APP_PORT}" pnpm e2e) \
  && ok "PC E2E 全绿" || { bad "PC E2E 失败"; exit 1; }

step "4. 构建并启动 PC 镜像"
docker build -q -f "$CONSOLE/Dockerfile" \
  --build-arg FRONTEND_OIDC_ENABLED=false \
  -t "oa-platform/oa-console:${TAG}" "$CONSOLE" >/dev/null \
  && ok "oa-console 镜像构建成功" || { bad "oa-console 镜像构建失败"; exit 1; }
OA_APP_PORT="$APP_PORT" OA_CONSOLE_PORT="$CONSOLE_PORT" OA_SECURITY_MODE=DEV OA_WORKFLOW_MODE=LOCAL \
  docker compose -p oa-platform -f "$COMPOSE" --profile apps up -d oa-console
for i in $(seq 1 30); do curl -sf "http://127.0.0.1:${CONSOLE_PORT}/healthz" >/dev/null && break; sleep 1; done
curl -sf "http://127.0.0.1:${CONSOLE_PORT}/healthz" >/dev/null && ok "PC 容器健康" || bad "PC 容器不健康"

step "5. nginx SPA 与四上游代理"
ROOT_HTML=$(curl -s "http://127.0.0.1:${CONSOLE_PORT}/")
echo "$ROOT_HTML" | grep -q '<div id="root"></div>' && ok "根页面可访问" || bad "根页面异常"
curl -s "http://127.0.0.1:${CONSOLE_PORT}/iam/sandbox" | grep -q '<div id="root"></div>' \
  && ok "history 路由回退正常" || bad "history 回退失败"
[ "$(curl -s -H 'X-OA-User: seed-user-1' "http://127.0.0.1:${CONSOLE_PORT}/api/v1/system/ping" | jq -r .code)" = "0" ] \
  && ok "oa-app 代理正常" || bad "oa-app 代理失败"
[ "$(curl -s -H 'X-OA-User: seed-user-1' "http://127.0.0.1:${CONSOLE_PORT}/api/v1/notify/ping" | jq -r .code)" = "0" ] \
  && ok "notify 代理正常" || bad "notify 代理失败"
[ "$(curl -s -H 'X-OA-User: seed-user-1' "http://127.0.0.1:${CONSOLE_PORT}/api/v1/file/ping" | jq -r .code)" = "0" ] \
  && ok "file 代理正常" || bad "file 代理失败"
[ "$(curl -s -H 'X-OA-User: seed-user-1' "http://127.0.0.1:${CONSOLE_PORT}/api/v1/job/ping" | jq -r .code)" = "0" ] \
  && ok "job 代理正常" || bad "job 代理失败"

echo; echo "════════════════════════════════"
echo "  Phase 3 PC：通过 ${PASS} / 失败 ${FAIL}"
echo "════════════════════════════════"
[ "$FAIL" -eq 0 ]
