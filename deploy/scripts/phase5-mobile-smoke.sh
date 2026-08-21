#!/usr/bin/env bash
# Phase 5 移动端发布关卡：静态门禁、真实四场景浏览器、镜像、SPA 与四上游代理。
# 复用现有万人夹具，不删库、不重置授权；每次只新增一条随后完成的请假单和一条已读公告。
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MOBILE="$ROOT/oa-mobile"
COMPOSE="$ROOT/deploy/docker-compose.yml"
APP_PORT="${OA_PHASE5_APP_PORT:-18400}"
MOBILE_PORT="${OA_MOBILE_PORT:-8405}"
TAG="${OA_IMAGE_TAG:-local}"
PASS=0; FAIL=0
ok(){ echo "  ✅ $1"; PASS=$((PASS+1)); }
bad(){ echo "  ❌ $1"; FAIL=$((FAIL+1)); }
step(){ echo; echo "── $1"; }
as_user(){ local user="$1"; shift; curl -sS -H "X-OA-User: $user" "$@"; }
code_of(){ jq -r '.code // -1'; }

step "1. 静态门禁"
(cd "$MOBILE" && pnpm install --frozen-lockfile && pnpm test && pnpm build && pnpm size) \
  && ok "移动端依赖、单测、构建与 220KB 首屏预算通过" || { bad "静态门禁失败"; exit 1; }
docker compose -p oa-platform -f "$COMPOSE" --profile apps config -q \
  && ok "compose 配置有效" || { bad "compose 配置无效"; exit 1; }

step "2. 从当前源码启动显式 DEV 后端"
OA_BUILD_CONSOLE=false OA_BUILD_MOBILE=false bash "$ROOT/deploy/build-images.sh" \
  && ok "当前源码的四个后端镜像构建成功" || { bad "后端镜像构建失败"; exit 1; }
OA_APP_PORT="$APP_PORT" OA_SECURITY_MODE=DEV OA_WORKFLOW_MODE=LOCAL OA_ORG_SEED_ENABLED=true \
  docker compose -p oa-platform -f "$COMPOSE" --profile apps up -d oa-app oa-notify oa-file oa-job
for _ in $(seq 1 45); do curl -sf "http://127.0.0.1:${APP_PORT}/api/v1/system/ping" >/dev/null && break; sleep 2; done
curl -sf "http://127.0.0.1:${APP_PORT}/api/v1/system/ping" >/dev/null \
  && ok "DEV 后端就绪" || { bad "DEV 后端未就绪"; exit 1; }
employees=$(docker exec oa-postgres psql -U "${OA_PG_USER:-oa}" -d "${OA_PG_DB:-oa}" -tAc \
  "select count(*) from oa_org.employee where status='ACTIVE'" 2>/dev/null | tr -d '[:space:]')
[ "$employees" = "10000" ] && ok "万人通讯录夹具就绪" \
  || { bad "当前员工数 ${employees:-0}，请先显式运行 Phase 3 夹具装载"; exit 1; }

step "3. 准备非破坏性真实业务夹具"
API="http://127.0.0.1:${APP_PORT}/api/v1"
NOTIFY="http://127.0.0.1:${OA_NOTIFY_PORT:-8401}/api/v1"
run_tag="mobile-$(date +%s)"
year="$(date +%Y)"
leave_day="${year}-12-31"
grant=$(as_user seed-user-1 -H 'Content-Type: application/json' -X POST "$API/flow/leave/balances/grant" \
  -d "{\"userId\":\"seed-user-65\",\"leaveTypeCode\":\"ANNUAL\",\"period\":\"${year}\",\"days\":1000}")
[ "$(code_of <<<"$grant")" = "0" ] || { bad "假期额度准备失败: $grant"; exit 1; }
leave=$(as_user seed-user-65 -H 'Content-Type: application/json' -X POST "$API/flow/leave" \
  -d "{\"leaveTypeCode\":\"ANNUAL\",\"startDate\":\"${leave_day}\",\"endDate\":\"${leave_day}\",\"days\":1,\"reason\":\"${run_tag}\"}")
[ "$(code_of <<<"$leave")" = "0" ] || { bad "真实待办创建失败: $leave"; exit 1; }
request_no=$(jq -r '.data.requestNo' <<<"$leave")
sleep 1
todos=$(as_user seed-user-1 "$API/flow/todos?limit=100")
task_id=$(jq -r '.data | map(select(.bizType=="LEAVE" and .applicantUserId=="seed-user-65")) | sort_by(.id) | last | .taskId // empty' <<<"$todos")
task_title=$(jq -r --arg id "$task_id" '.data[] | select(.taskId==$id) | .title' <<<"$todos")
[ -n "$task_id" ] && [ -n "$task_title" ] || { bad "审批人没有收到真实待办: $todos"; exit 1; }
announcement_title="移动验收-${run_tag}"
announcement=$(as_user seed-user-1 -H 'Content-Type: application/json' -X POST "$NOTIFY/announcements" \
  -d "{\"title\":\"${announcement_title}\",\"content\":\"移动端公告已读链路验收\",\"recipients\":[\"seed-user-1\"],\"expireAt\":null}")
[ "$(code_of <<<"$announcement")" = "0" ] || { bad "公告夹具创建失败: $announcement"; exit 1; }
announcement_id=$(jq -r '.data.id' <<<"$announcement")
ok "真实请假待办与公告已创建"

step "4. 390×844 真实浏览器四场景"
(cd "$MOBILE" && \
  VITE_API_TARGET="http://127.0.0.1:${APP_PORT}" \
  VITE_NOTIFY_TARGET="http://127.0.0.1:${OA_NOTIFY_PORT:-8401}" \
  OA_MOBILE_TODO_TITLE="$task_title" OA_MOBILE_TODO_TASK="$task_id" \
  OA_MOBILE_ANNOUNCEMENT_TITLE="$announcement_title" pnpm e2e) \
  && ok "打卡、待办办理、通讯录、公告已读浏览器链路通过" || { bad "移动端 E2E 失败"; exit 1; }
[ "$(as_user seed-user-65 "$API/flow/leave/${request_no}" | jq -r '.data.status')" = "APPROVED" ] \
  && ok "待办办理已回写请假单 APPROVED" || bad "请假单未完成"
[ "$(as_user seed-user-1 "$NOTIFY/announcements?limit=100" | jq -r --argjson id "$announcement_id" '.data[] | select(.id==$id) | .readByMe')" = "true" ] \
  && ok "公告已读已持久化" || bad "公告已读未持久化"

step "5. 生产镜像、SPA 与同源代理"
docker build -q -f "$MOBILE/Dockerfile" --build-arg FRONTEND_OIDC_ENABLED=false \
  -t "oa-platform/oa-mobile:${TAG}" "$MOBILE" >/dev/null \
  && ok "oa-mobile 镜像构建成功" || { bad "移动端镜像构建失败"; exit 1; }
OA_APP_PORT="$APP_PORT" OA_MOBILE_PORT="$MOBILE_PORT" OA_SECURITY_MODE=DEV OA_WORKFLOW_MODE=LOCAL OA_ORG_SEED_ENABLED=true \
  docker compose -p oa-platform -f "$COMPOSE" --profile apps up -d --no-deps --force-recreate oa-mobile
for _ in $(seq 1 30); do curl -sf "http://127.0.0.1:${MOBILE_PORT}/healthz" >/dev/null && break; sleep 1; done
curl -sf "http://127.0.0.1:${MOBILE_PORT}/healthz" >/dev/null && ok "移动端容器健康" || bad "移动端容器不健康"
for route in / /workbench /attendance /directory /announcements /callback; do
  curl -sf "http://127.0.0.1:${MOBILE_PORT}${route}" | grep -q '<div id="root"></div>' \
    || { bad "SPA 路由 ${route} 回退失败"; continue; }
done
[ "$FAIL" -eq 0 ] && ok "6 个 SPA 深链路由回退正常"
for endpoint in system/ping notify/ping file/ping job/ping; do
  [ "$(as_user seed-user-1 "http://127.0.0.1:${MOBILE_PORT}/api/v1/${endpoint}" | code_of)" = "0" ] \
    || bad "同源代理 ${endpoint} 失败"
done
[ "$FAIL" -eq 0 ] && ok "四个后端同源代理正常"

echo; echo "════════════════════════════════"
echo "  Phase 5 Mobile：通过 ${PASS} / 失败 ${FAIL}"
echo "════════════════════════════════"
[ "$FAIL" -eq 0 ]
