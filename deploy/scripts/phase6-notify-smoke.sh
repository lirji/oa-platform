#!/usr/bin/env bash
# Phase 6 通知域冒烟：站内信 / WebSocket 长连 / 万人公告广播 / 位图已读回执 / 越权与幂等。
#
# 验收线（FINAL_PLAN §13）：全员公告 10,000 人推送完成 < 30s；已读回执存储 < 100KB。
# ⚠️ 占用 8400（oa-app）与 8401（notify），若同名容器在跑会先停掉。
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DEPLOY="$ROOT/deploy"
BASE="http://localhost:8400/api/v1"
N="http://localhost:8401/api/v1"
ADMIN="seed-user-1"
PASS=0; FAIL=0
ok()   { echo "  ✅ $1"; PASS=$((PASS+1)); }
bad()  { echo "  ❌ $1"; FAIL=$((FAIL+1)); }
step() { echo; echo "── $1"; }

export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 21)}"
export PATH="/Users/liruijun/personal/devUtils/apache-maven-3.9.12/bin:$PATH"
set -a; [ -f "$DEPLOY/.env" ] && . "$DEPLOY/.env"; set +a
PG_USER="${OA_PG_USER:-oa}"; PG_DB="${OA_PG_DB:-oa}"
psq() { docker exec oa-postgres psql -U "$PG_USER" -d "$PG_DB" -tAc "$1" 2>/dev/null | tr -d ' \r'; }
as()  { curl -s -H "X-OA-User: $1" "${@:2}"; }
code(){ as "$1" "${@:2}" | jq -r '.code // -1'; }

step "1. 构建并启动 oa-app(8400) + oa-notify(8401)"
# ★ 必须 clean：spring-boot:repackage 增量构建会保留【旧的嵌套依赖 jar】，
#   于是新加的 Flyway 迁移静默不进 fat jar，表现为"迁移文件在、但没执行、也不报错"。
mvn -B -q -f "$ROOT/pom.xml" clean install -DskipTests >/tmp/oa-p6-build.log 2>&1 \
  && ok "构建通过（clean，避免陈旧嵌套 jar）" || { bad "构建失败"; tail -25 /tmp/oa-p6-build.log; exit 1; }
docker stop oa-app oa-notify >/dev/null 2>&1
pkill -f 'oa-app-.*\.jar' 2>/dev/null; pkill -f 'oa-notify-service-.*\.jar' 2>/dev/null; sleep 2
APP_JAR=$(ls "$ROOT"/oa-app/target/oa-app-*.jar | grep -v original | head -1)
NTF_JAR=$(ls "$ROOT"/oa-notify-service/target/oa-notify-service-*.jar | grep -v original | head -1)
OA_ORG_SEED_ENABLED=true OA_BOOTSTRAP_ADMIN="$ADMIN" OA_WORKFLOW_MODE=LOCAL \
  java -jar "$APP_JAR" >/tmp/oa-app-phase6.log 2>&1 &
APP_PID=$!
java -jar "$NTF_JAR" >/tmp/oa-notify-phase6.log 2>&1 &
NTF_PID=$!
cleanup() { kill $APP_PID $NTF_PID 2>/dev/null; wait $APP_PID $NTF_PID 2>/dev/null; }
trap cleanup EXIT
for i in $(seq 1 45); do curl -sf "$BASE/system/ping" >/dev/null 2>&1 && break; sleep 2; done
for i in $(seq 1 45); do curl -sf "$N/notify/ping"   >/dev/null 2>&1 && break; sleep 2; done
curl -sf "$BASE/system/ping" >/dev/null && ok "oa-app 就绪" || { bad "oa-app 启动失败"; tail -30 /tmp/oa-app-phase6.log; exit 1; }
curl -sf "$N/notify/ping"    >/dev/null && ok "oa-notify 就绪" || { bad "notify 启动失败"; tail -30 /tmp/oa-notify-phase6.log; exit 1; }

step "2. 独立服务自建 schema 与自有迁移史"
[ "$(psq "SELECT count(*) FROM pg_tables WHERE schemaname='oa_notify'")" -ge 6 ] \
  && ok "oa_notify schema 由 notify 自己迁移出来" || bad "oa_notify 表不全"
[ -n "$(psq "SELECT 1 FROM pg_tables WHERE tablename='flyway_schema_history_notify'")" ] \
  && ok "用的是独立迁移史（与 oa-app 不共用一张历史表）" || bad "没有独立迁移史"
[ "$(psq "SELECT count(*) FROM oa_iam.permission WHERE module='notify'")" = "7" ] \
  && ok "7 个通知权限点已进目录（目录里没有的 code 一律判拒绝）" || bad "通知权限点缺失"

step "3. 装载一万人"
as "$ADMIN" -X DELETE "$BASE/org/seed" >/dev/null 2>&1
psq "TRUNCATE oa_notify.notification, oa_notify.notification_dedup, oa_notify.announcement,
     oa_notify.announcement_read, oa_notify.announcement_audience, oa_notify.channel_log,
     oa_notify.recipient RESTART IDENTITY CASCADE" >/dev/null
SEED=$(as "$ADMIN" -X POST "$BASE/org/seed?orgs=1000&employees=10000")
[ "$(echo "$SEED" | jq -r '.data.employees // 0')" = "10000" ] && ok "10,000 员工就位" \
  || { bad "装载失败：$SEED"; exit 1; }
R_HR=$(psq "SELECT id FROM oa_iam.role WHERE code='HR_ADMIN'")
as "$ADMIN" -X POST "$BASE/iam/grants" -H 'Content-Type: application/json' \
  -d "{\"subjectType\":\"USER\",\"subjectId\":\"${ADMIN}\",\"roleId\":${R_HR},\"scopeType\":\"ALL\"}" >/dev/null
sleep 1

step "4. 站内信：发送 / 未读数 / 标记已读"
as "$ADMIN" -X POST "$N/notify/messages" -H 'Content-Type: application/json' \
  -d '{"userIds":["seed-user-11"],"category":"TODO","title":"你有一条待办","content":"请假单待审","bizType":"LEAVE","bizId":"L-1"}' >/dev/null
[ "$(as seed-user-11 "$N/notify/messages/unread-count" | jq -r '.data.unread')" = "1" ] \
  && ok "收件人未读数 = 1" || bad "未读数不对"
MID=$(as seed-user-11 "$N/notify/messages" | jq -r '.data[0].id')
as seed-user-11 -X POST "$N/notify/messages/$MID/read" >/dev/null
[ "$(as seed-user-11 "$N/notify/messages/unread-count" | jq -r '.data.unread')" = "0" ] \
  && ok "标记已读后未读数归零" || bad "标记已读无效"

step "5. 幂等：同一 dedupKey 重放不该刷出第二条"
A=$(as "$ADMIN" -X POST "$N/notify/messages" -H 'Content-Type: application/json' \
     -d '{"userIds":["seed-user-12"],"title":"幂等","dedupKey":"smoke-idem"}' | jq -r '.data.inserted')
B=$(as "$ADMIN" -X POST "$N/notify/messages" -H 'Content-Type: application/json' \
     -d '{"userIds":["seed-user-12"],"title":"幂等","dedupKey":"smoke-idem"}' | jq -r '.data.inserted')
[ "$A" = "1" ] && [ "$B" = "0" ] && ok "重放被去重（1 → 0）" \
  || bad "幂等失效：第一次 ${A}、第二次 ${B}"
# 这条断言的由来：唯一索引原先建在分区表上、被迫含 created_at，等于"同一时刻才算重复"，
# 完全不去重。分区表做不了跨时间唯一，幂等键必须放在不分区的小表上。
[ -n "$(psq "SELECT 1 FROM pg_tables WHERE schemaname='oa_notify' AND tablename='notification_dedup'")" ] \
  && ok "幂等键落在【不分区】的独立表上" || bad "notification_dedup 不存在"

step "6. WebSocket 长连：推送真的送到了在线用户"
rm -f /tmp/oa-wsprobe.out
( java "$DEPLOY/scripts/WsProbe.java" ws://localhost:8401/ws seed-user-13 1 20 >/tmp/oa-wsprobe.out 2>&1 & )
for i in $(seq 1 20); do grep -q CONNECTED /tmp/oa-wsprobe.out 2>/dev/null && break; sleep 1; done
grep -q CONNECTED /tmp/oa-wsprobe.out && ok "长连握手成功" || bad "长连连不上"
as "$ADMIN" -X POST "$N/notify/messages" -H 'Content-Type: application/json' \
  -d '{"userIds":["seed-user-13"],"title":"长连推送","content":"hi","dedupKey":"smoke-ws"}' >/dev/null
for i in $(seq 1 20); do grep -q RECEIVED /tmp/oa-wsprobe.out 2>/dev/null && break; sleep 1; done
[ "$(grep -o 'RECEIVED=[0-9]*' /tmp/oa-wsprobe.out | cut -d= -f2)" = "1" ] \
  && ok "在线用户即时收到推送" || bad "长连没收到推送（$(cat /tmp/oa-wsprobe.out | tr '\n' ' '))"

step "7. ★ 万人公告广播（验收线 < 30s）"
psq "SELECT string_agg('\"seed-user-'||id||'\"', ',') FROM oa_org.employee" > /tmp/oa-p6-users.csv
python3 - <<'PY'
ids = open('/tmp/oa-p6-users.csv').read().strip()
open('/tmp/oa-p6-ann.json','w').write(
    '{"title":"全员公告：系统升级","content":"本周六 22:00-24:00 维护。","recipients":[%s]}' % ids)
PY
now_ms() { python3 -c 'import time;print(int(time.time()*1000))'; }   # BSD date 不支持 %3N，且不报错
T0=$(now_ms)
ANN=$(as "$ADMIN" -X POST "$N/announcements" -H 'Content-Type: application/json' --data-binary @/tmp/oa-p6-ann.json)
T1=$(now_ms)
ANN_ID=$(echo "$ANN" | jq -r '.data.id')
ELAPSED=$(( T1 - T0 ))
[ "$(echo "$ANN" | jq -r '.data.audience')" = "10000" ] && ok "受众 10,000 人" || bad "受众数不对：$ANN"
[ "$ELAPSED" -lt 30000 ] && ok "广播耗时 ${ELAPSED} ms（验收线 30,000 ms）" || bad "广播超时 ${ELAPSED} ms"
[ "$(psq "SELECT count(*) FROM oa_notify.notification WHERE biz_type='ANNOUNCEMENT' AND biz_id='${ANN_ID}'")" = "10000" ] \
  && ok "10,000 条站内信全部落库（落库是唯一的送达保证，长连只是加速）" || bad "站内信落库数不对"

step "8. ★ 位图已读回执（验收线 < 100KB）"
psq "SELECT 'seed-user-'||id FROM oa_org.employee ORDER BY id LIMIT 8000" > /tmp/oa-p6-readers.txt
xargs -P 50 -I{} curl -s -o /dev/null -H "X-OA-User: {}" -X POST "$N/announcements/${ANN_ID}/read" < /tmp/oa-p6-readers.txt
STATS=$(as "$ADMIN" "$N/announcements/${ANN_ID}/stats?unreadSample=3")
RC=$(echo "$STATS" | jq -r '.data.readCount'); UC=$(echo "$STATS" | jq -r '.data.unreadCount')
BYTES=$(echo "$STATS" | jq -r '.data.bitmapBytes')
[ "$RC" = "8000" ] && ok "已读 8,000 人" || bad "已读数应为 8000，实为 ${RC}"
[ "$UC" = "2000" ] && ok "未读 2,000 人（受众位图 ANDNOT 已读位图，纯位运算）" || bad "未读数应为 2000，实为 ${UC}"
[ "$BYTES" -lt 102400 ] && ok "回执位图仅 ${BYTES} 字节（验收线 102,400；runOptimize 把连续区间压成 run 编码）" \
  || bad "位图 ${BYTES} 字节超过 100KB"
[ "$(echo "$STATS" | jq -r '.data.sampleUnread[0]')" = "seed-user-8001" ] \
  && ok "能从位图反查出具体未读人" || bad "未读人反查不对"
# 明细表对照：同样的信息若按"每人一行"记，是 8,000 行
echo "     对照：明细表记法需要 8,000 行，位图 ${BYTES} 字节"

step "9. 越权：权限拒绝必须是 403 + 结构化 body，不是 500"
HTTP=$(curl -s -o /tmp/oa-p6-403.json -w '%{http_code}' -H "X-OA-User: seed-user-9000" \
       -X POST "$N/notify/messages" -H 'Content-Type: application/json' -d '{"userIds":["seed-user-7"],"title":"x"}')
[ "$HTTP" = "403" ] && ok "普通员工发站内信被拒（HTTP 403）" || bad "应 403，实为 ${HTTP}"
[ "$(jq -r '.code' /tmp/oa-p6-403.json)" = "3001" ] \
  && ok "返回体是 Result(3001)，前端能区分'无权限'与'服务器坏了'" || bad "403 的 body 不是结构化 Result"
[ "$(curl -s -o /dev/null -w '%{http_code}' -H "X-OA-User: seed-user-9000" -X POST "$N/announcements" \
     -H 'Content-Type: application/json' -d '{"title":"x","content":"y","recipients":["seed-user-7"]}')" = "403" ] \
  && ok "普通员工发公告被拒" || bad "普通员工能发公告"
[ "$(curl -s -o /dev/null -w '%{http_code}' -H "X-OA-User: seed-user-9000" "$N/announcements/${ANN_ID}/stats")" = "403" ] \
  && ok "普通员工看不到已读统计" || bad "已读统计对普通员工开放了"

step "10. IDOR：不能标记别人的站内信"
VICTIM=$(psq "SELECT id FROM oa_notify.notification WHERE user_id='seed-user-14' ORDER BY id DESC LIMIT 1")
if [ -z "$VICTIM" ]; then
  as "$ADMIN" -X POST "$N/notify/messages" -H 'Content-Type: application/json' \
    -d '{"userIds":["seed-user-14"],"title":"IDOR 靶子","dedupKey":"smoke-idor"}' >/dev/null
  VICTIM=$(psq "SELECT id FROM oa_notify.notification WHERE user_id='seed-user-14' ORDER BY id DESC LIMIT 1")
fi
as seed-user-15 -X POST "$N/notify/messages/${VICTIM}/read" >/dev/null
[ -z "$(psq "SELECT 1 FROM oa_notify.notification WHERE id=${VICTIM} AND read_at IS NOT NULL")" ] \
  && ok "改不动别人的消息（WHERE 里带 user_id 是最后一道防线）" || bad "IDOR：别人的消息被改成已读了"

step "11. 撤回公告"
as "$ADMIN" -X POST "$N/announcements/${ANN_ID}/revoke" >/dev/null
[ "$(psq "SELECT status FROM oa_notify.announcement WHERE id=${ANN_ID}")" = "REVOKED" ] \
  && ok "公告已撤回" || bad "撤回失败"
[ "$(psq "SELECT count(*) FROM oa_notify.notification WHERE biz_type='ANNOUNCEMENT' AND biz_id='${ANN_ID}'")" = "0" ] \
  && ok "连带撤掉站内信（留着的话点进去看到一条已撤回的公告，比看不到更困惑）" || bad "站内信没被撤掉"

step "12. 外部渠道默认全关"
[ "$(as "$ADMIN" "$N/notify/ws-stats" | jq -r '.data.channels | length')" = "0" ] \
  && ok "邮件/短信/企微/飞书默认关闭（引入即安全）" || bad "有渠道被默认打开了"

echo; echo "════════════════════════════════════════"
echo "  Phase 6 通知域冒烟：通过 ${PASS} / 失败 ${FAIL}"
echo "════════════════════════════════════════"
[ "$FAIL" -eq 0 ] || exit 1
