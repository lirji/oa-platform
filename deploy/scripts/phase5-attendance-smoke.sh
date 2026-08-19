#!/usr/bin/env bash
# Phase 5 考勤冒烟：打卡削峰链路 + 早高峰压测 + 日结。
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DEPLOY="$ROOT/deploy"
BASE="http://localhost:8400/api/v1"
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
rc()  { as "$1" "${@:2}" | jq -r '.code // -1'; }

step "1. 构建并启动"
mvn -B -q -f "$ROOT/pom.xml" clean install -DskipTests >/tmp/oa-p5-build.log 2>&1 \
  && ok "构建通过" || { bad "构建失败"; tail -25 /tmp/oa-p5-build.log; exit 1; }
pkill -f 'oa-app-.*\.jar' 2>/dev/null; sleep 1
JAR=$(ls "$ROOT"/oa-app/target/oa-app-*.jar | grep -v original | head -1)
OA_ORG_SEED_ENABLED=true OA_BOOTSTRAP_ADMIN="$ADMIN" OA_WORKFLOW_MODE=LOCAL \
  java -jar "$JAR" >/tmp/oa-app-phase5.log 2>&1 &
APP_PID=$!
cleanup() { kill $APP_PID 2>/dev/null; wait $APP_PID 2>/dev/null; }
trap cleanup EXIT
for i in $(seq 1 45); do curl -sf "$BASE/system/ping" >/dev/null 2>&1 && break; sleep 2; done
for i in $(seq 1 20); do [ "$(rc "$ADMIN" "$BASE/iam/admin/cache-stats")" = "0" ] && break; sleep 1; done
[ "$(rc "$ADMIN" "$BASE/iam/admin/cache-stats")" = "0" ] && ok "应用就绪" || { bad "启动失败"; tail -40 /tmp/oa-app-phase5.log; exit 1; }

step "2. 装载 10,000 员工，并用【一条部门授权】让全员获得打卡权限"
as "$ADMIN" -X DELETE "$BASE/org/seed" >/dev/null 2>&1
psq "DELETE FROM oa_iam.grant_record WHERE subject_id LIKE 'seed-user-%' AND subject_id <> '${ADMIN}'" >/dev/null
psq "DELETE FROM oa_iam.grant_record WHERE subject_type='ORG_UNIT'" >/dev/null
psq "TRUNCATE oa_att.punch_record, oa_att.attendance_daily, oa_att.punch_dead_letter" >/dev/null
# ⚠️ 同时要清 Redis 幂等键：只 TRUNCATE 表的话，上一轮留下的键会让这一轮全部被判成"已打卡"。
# 这也顺带说明一件事——Redis 与数据库可能不同步，所以数据库上的唯一约束才是真正的幂等底线。
docker exec oa-redis redis-cli --scan --pattern 'oa:punch:*' 2>/dev/null | xargs -r docker exec -i oa-redis redis-cli DEL >/dev/null 2>&1
docker exec oa-redis redis-cli FLUSHDB >/dev/null 2>&1
SEED=$(as "$ADMIN" -X POST "$BASE/org/seed?orgs=3000&employees=10000")
[ "$(echo "$SEED" | jq -r '.data.employees // 0')" = "10000" ] && ok "10,000 员工就位" || { bad "装载失败"; exit 1; }
sleep 2
ROOT_ORG=$(psq "SELECT id FROM oa_org.org_unit WHERE parent_id IS NULL")
R_EMP=$(psq "SELECT id FROM oa_iam.role WHERE code='EMPLOYEE'")
as "$ADMIN" -X POST "$BASE/iam/grants" -H 'Content-Type: application/json' \
  -d "{\"subjectType\":\"ORG_UNIT\",\"subjectId\":\"${ROOT_ORG}\",\"roleId\":${R_EMP},\"scopeType\":\"SELF\",\"includeDescendants\":true}" >/dev/null
sleep 2
[ "$(rc "seed-user-5000" "$BASE/attendance/me")" = "0" ] \
  && ok "一条根组织授权 → 10,000 人全部继承到打卡权限（部门继承在万人规模下生效）" || bad "部门继承未覆盖全员"

step "3. 幂等：同一个人重复打卡只留一条"
as "seed-user-2" -X POST "$BASE/attendance/punch?type=IN" >/dev/null
DUPMSG=$(as "seed-user-2" -X POST "$BASE/attendance/punch?type=IN" | jq -r '.data.duplicate')
[ "$DUPMSG" = "true" ] && ok "第二次打卡被识别为重复（Redis 幂等键在第一跳挡下）" || bad "重复打卡未被识别"

step "4. ★ 早高峰压测：10,000 人同时打上班卡（吞吐）"
LOAD=$("$JAVA_HOME/bin/java" "$ROOT/deploy/scripts/PunchLoadTest.java" http://localhost:8400 10000 300 IN 2>&1 | grep "^RESULT")
echo "     $LOAD"
QPS=$(echo "$LOAD" | sed -n 's/.*qps=\([0-9]*\).*/\1/p')
FAILN=$(echo "$LOAD" | sed -n 's/.*fail=\([0-9]*\).*/\1/p')
OKN=$(echo "$LOAD" | sed -n 's/.*ok=\([0-9]*\).*/\1/p')
DUPN=$(echo "$LOAD" | sed -n 's/.*dup=\([0-9]*\).*/\1/p')
[ "$FAILN" = "0" ] && ok "10,000 次打卡零失败" || bad "有 ${FAILN} 次失败"
# 第 3 步的幂等测试已经替 seed-user-2 打过卡，所以这里必然有 1 次 dup —— 断言 ok+dup 才对
[ "$((OKN + DUPN))" = "10000" ] && ok "10,000 次全部有确定结果（受理 ${OKN} + 幂等挡下 ${DUPN}）" || bad "只处理 $((OKN + DUPN)) 次"
[ "$QPS" -ge 500 ] 2>/dev/null && ok "达成 ${QPS} QPS（验收线 500）" || bad "只有 ${QPS} QPS，未达 500"

step "4b. 单请求延迟（低并发，测服务端真实处理时间）"
# ⚠️ 上一步 300 并发下的延迟 ≈ 并发数 ÷ 吞吐（Little 定律），量的是【客户端排队】而不是服务端能力。
# 要看"入队即返回"到底有多快，必须把客户端并发压低到不排队的水平。
LAT=$("$JAVA_HOME/bin/java" "$ROOT/deploy/scripts/PunchLoadTest.java" http://localhost:8400 2000 20 OUT 2>&1 | grep "^RESULT")
echo "     $LAT"
P99=$(echo "$LAT" | sed -n 's/.*p99ms=\([0-9.]*\).*/\1/p')
P50=$(echo "$LAT" | sed -n 's/.*p50ms=\([0-9.]*\).*/\1/p')
awk -v p="$P99" 'BEGIN{exit !(p < 100)}' \
  && ok "低并发下打卡 P50=${P50}ms P99=${P99}ms（入队即返回，不等落库）" \
  || bad "低并发下 P99 仍有 ${P99}ms，说明入队路径本身就慢"

step "5. 落库正确性：一条不丢、一条不重"
sleep 2
STATS=$(as "$ADMIN" "$BASE/attendance/admin/stats")
echo "     $(echo "$STATS" | jq -c '.data.pipeline')"
echo "     $(echo "$STATS" | jq -c '.data.storage')"
CNT=$(echo "$STATS" | jq -r '.data.storage.punchCount')
DUPC=$(echo "$STATS" | jq -r '.data.storage.duplicates')
DEAD=$(echo "$STATS" | jq -r '.data.storage.deadLetters')
[ "$CNT" -ge 12000 ] 2>/dev/null && ok "库里 ${CNT} 条打卡记录（10,000 上班 + 2,000 下班）" || bad "只落库 ${CNT} 条，有丢失"
[ "$DUPC" = "0" ] && ok "零重复打卡（Redis + 数据库唯一约束双闸门）" || bad "出现 ${DUPC} 组重复"
[ "$DEAD" = "0" ] && ok "零死信" || bad "有 ${DEAD} 条死信"

step "6. 分区表"
PARTS=$(psq "SELECT count(*) FROM pg_inherits WHERE inhparent = 'oa_att.punch_record'::regclass")
[ "$PARTS" -ge 12 ] 2>/dev/null && ok "punch_record 已分成 ${PARTS} 个分区（按月 + 兜底）" || bad "分区数 ${PARTS} 异常"
INPART=$(psq "SELECT count(*) FROM oa_att.punch_record_$(date +%Y%m)")
[ "$INPART" -ge 12000 ] 2>/dev/null && ok "数据落进了当月分区（${INPART} 条）" || bad "当月分区只有 ${INPART} 条"
DEFP=$(psq "SELECT count(*) FROM oa_att.punch_record_default")
[ "$DEFP" = "0" ] && ok "兜底分区为空（说明月份分区覆盖正确）" || bad "兜底分区有 ${DEFP} 条，说明分区没覆盖到"

step "7. 日结跑批"
COMPUTE=$(as "$ADMIN" -X POST "$BASE/attendance/daily-compute")
EMP=$(echo "$COMPUTE" | jq -r '.data.employees')
MS=$(echo "$COMPUTE" | jq -r '.data.elapsedMs')
echo "     $(echo "$COMPUTE" | jq -c '.data.byStatus')"
[ "$EMP" -ge 10000 ] 2>/dev/null && ok "日结 ${EMP} 人，耗时 ${MS}ms（聚合全在数据库里完成）" || bad "日结只算了 ${EMP} 人"
[ "$MS" -lt 10000 ] 2>/dev/null && ok "万人日结 < 10 秒" || bad "日结耗时 ${MS}ms 偏长"
MISSING=$(psq "SELECT count(*) FROM oa_att.attendance_daily WHERE status='MISSING'")
COMPLETE=$(psq "SELECT count(*) FROM oa_att.attendance_daily WHERE last_out IS NOT NULL")
[ "$MISSING" -ge 7000 ] 2>/dev/null && ok "只打了上班卡的 ${MISSING} 人被标为 MISSING（缺卡识别正确）" || bad "缺卡识别异常: ${MISSING}"
[ "$COMPLETE" -ge 2000 ] 2>/dev/null && ok "打了下班卡的 ${COMPLETE} 人算出了工时" || bad "工时计算异常: ${COMPLETE}"

echo; echo "════════════════════════════════════"
echo "  Phase 5 考勤冒烟：通过 $PASS / 失败 $FAIL"
echo "════════════════════════════════════"
[ "$FAIL" -eq 0 ] || exit 1
