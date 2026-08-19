#!/usr/bin/env bash
# Phase 7 文档域 + 行政域冒烟。
#
# 验收线（FINAL_PLAN §13 / §9.4）：
#   · 会议室并发预定 100 请求只成功 1 条
#   · 知识库开关打开后跨部门共享判权正确、关闭后回退无影响
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
http(){ curl -s -o /dev/null -w '%{http_code}' -H "X-OA-User: $1" "${@:2}"; }

step "1. 构建并启动"
# clean 是必须的：repackage 增量会保留旧的嵌套依赖 jar，新迁移会静默不进 fat jar。
mvn -B -q -f "$ROOT/pom.xml" clean install -DskipTests >/tmp/oa-p7-build.log 2>&1 \
  && ok "构建通过" || { bad "构建失败"; tail -25 /tmp/oa-p7-build.log; exit 1; }
docker stop oa-app >/dev/null 2>&1
pkill -f 'oa-app-.*\.jar' 2>/dev/null; sleep 2
# ★ 清 Redis 再启动，顺序不能反：权限快照 L1 在进程内、L2 在 Redis。
#   先启动再清 Redis，L1 已经从旧 L2 装载好了，清了也没用（本轮排查踩过）。
docker exec oa-redis redis-cli FLUSHDB >/dev/null 2>&1
JAR=$(ls "$ROOT"/oa-app/target/oa-app-*.jar | grep -v original | head -1)
OA_ORG_SEED_ENABLED=true OA_BOOTSTRAP_ADMIN="$ADMIN" OA_WORKFLOW_MODE=LOCAL \
  java -jar "$JAR" >/tmp/oa-app-phase7.log 2>&1 &
APP_PID=$!
cleanup() { kill $APP_PID 2>/dev/null; wait $APP_PID 2>/dev/null; }
trap cleanup EXIT
for i in $(seq 1 45); do curl -sf "$BASE/system/ping" >/dev/null 2>&1 && break; sleep 2; done
curl -sf "$BASE/system/ping" >/dev/null && ok "应用就绪" || { bad "启动失败"; tail -30 /tmp/oa-app-phase7.log; exit 1; }

step "2. 迁移与约束"
[ "$(psq "SELECT count(*) FROM pg_tables WHERE schemaname='oa_admin'")" -ge 8 ] \
  && ok "行政域 8 张表就位" || bad "oa_admin 表不全"
[ "$(psq "SELECT count(*) FROM pg_tables WHERE schemaname='oa_doc'")" -ge 5 ] \
  && ok "文档域 5 张表就位" || bad "oa_doc 表不全"
[ "$(psq "SELECT count(*) FROM pg_constraint WHERE conname IN ('ex_room_no_overlap','ex_vehicle_no_overlap')")" = "2" ] \
  && ok "会议室与车辆的排他约束都在（EXCLUDE USING gist）" || bad "排他约束缺失"
[ -n "$(psq "SELECT 1 FROM pg_tables WHERE schemaname='oa_sys' AND tablename='id_segment'")" ] \
  && ok "号段表已归位 oa_sys（oa-doc 不必依赖 oa-flow 才能拿文号）" || bad "id_segment 未归位"
[ -z "$(psq "SELECT 1 FROM pg_tables WHERE schemaname='oa_flow' AND tablename='id_segment'")" ] \
  && ok "旧表已移除（V50 搬运后 DROP）" || bad "oa_flow.id_segment 还在"
[ "$(psq "SELECT count(*) FROM oa_iam.permission WHERE module IN ('doc','admin')")" = "19" ] \
  && ok "19 个权限点进目录" || bad "Phase 7 权限点数不对：$(psq "SELECT count(*) FROM oa_iam.permission WHERE module IN ('doc','admin')")"

step "3. 装载与授权（一律走 API，不改库）"
# ★ 用裸 SQL 改 grant_record 会绕过失效协议：epoch 不 bump，L1/L2 里的旧快照继续放行。
#   本轮排查在这上面花了最久 —— 影子校验（consistent=false）才把它指出来。
as "$ADMIN" -X DELETE "$BASE/org/seed" >/dev/null 2>&1
psq "DELETE FROM oa_admin.room_booking; DELETE FROM oa_admin.vehicle_booking;
     DELETE FROM oa_admin.visitor; DELETE FROM oa_admin.supply_request;
     DELETE FROM oa_admin.asset_txn; DELETE FROM oa_admin.asset;
     DELETE FROM oa_doc.kb_share; DELETE FROM oa_doc.kb_doc; DELETE FROM oa_doc.doc_flow_log;
     DELETE FROM oa_doc.official_doc" >/dev/null
SEED=$(as "$ADMIN" -X POST "$BASE/org/seed?orgs=200&employees=800")
[ "$(echo "$SEED" | jq -r '.data.employees // 0')" = "800" ] && ok "800 员工就位" || { bad "装载失败"; exit 1; }
R_EMP=$(psq "SELECT id FROM oa_iam.role WHERE code='EMPLOYEE'")
R_HR=$(psq "SELECT id FROM oa_iam.role WHERE code='HR_ADMIN'")
as "$ADMIN" -X POST "$BASE/iam/grants" -H 'Content-Type: application/json' \
  -d "{\"subjectType\":\"USER\",\"subjectId\":\"${ADMIN}\",\"roleId\":${R_HR},\"scopeType\":\"ALL\"}" >/dev/null
# ★ 用 xargs -P 而不是 `cmd & ... wait`：本脚本自己用 & 起了 java 应用，
#   无参的 wait 会连它一起等 —— 应用永远不退出，脚本就永远卡在这里，
#   而且卡住的位置离真正的原因（几十行前的 APP_PID=$!）很远，极难看出来。
seq 100 199 | xargs -P 20 -I{} curl -s -o /dev/null -H "X-OA-User: $ADMIN" \
  -X POST "$BASE/iam/grants" -H 'Content-Type: application/json' \
  -d "{\"subjectType\":\"USER\",\"subjectId\":\"seed-user-{}\",\"roleId\":${R_EMP},\"scopeType\":\"SELF\"}"
ok "100 名员工授予 EMPLOYEE，管理员授予 HR_ADMIN"
[ "$(http seed-user-500 -X POST "$BASE/admin-biz/rooms/bookings" -H 'Content-Type: application/json' \
     -d '{"roomId":1,"subject":"x","startAt":"2026-12-01T10:00:00+08:00","endAt":"2026-12-01T11:00:00+08:00","attendees":2}')" = "403" ] \
  && ok "未授权用户被拒（403）—— 新接口的判权确实生效" || bad "未授权用户能订会议室"

step "4. ★ 会议室并发预定：100 请求只成功 1 条"
ROOM=$(psq "SELECT id FROM oa_admin.meeting_room WHERE code='R-A101'")
SS="2026-10-01T10:00:00+08:00"; SE="2026-10-01T11:00:00+08:00"
RESULT=$(seq 100 199 | xargs -P 100 -I{} curl -s -o /dev/null -w "%{http_code}\n" \
  -H "X-OA-User: seed-user-{}" -X POST "$BASE/admin-biz/rooms/bookings" -H 'Content-Type: application/json' \
  -d "{\"roomId\":${ROOM},\"subject\":\"抢会议室\",\"startAt\":\"${SS}\",\"endAt\":\"${SE}\",\"attendees\":5}")
N200=$(echo "$RESULT" | grep -c '^200$'); N409=$(echo "$RESULT" | grep -c '^409$')
[ "$N200" = "1" ] && ok "恰好 1 个 200" || bad "成功数应为 1，实为 ${N200}"
[ "$N409" = "99" ] && ok "其余 99 个 409（数据库排他约束判定，不是应用层加锁）" || bad "409 数应为 99，实为 ${N409}"
[ "$(psq "SELECT count(*) FROM oa_admin.room_booking WHERE room_id=${ROOM} AND status='BOOKED'")" = "1" ] \
  && ok "库里确实只有 1 条" || bad "库里预定数不对"

step "5. 时间区间语义：[) 左闭右开"
book() { http "$1" -X POST "$BASE/admin-biz/rooms/bookings" -H 'Content-Type: application/json' \
          -d "{\"roomId\":${ROOM},\"subject\":\"$2\",\"startAt\":\"$3\",\"endAt\":\"$4\",\"attendees\":$5}"; }
RC=$(book seed-user-100 "紧邻下一场" "${SE}" "2026-10-01T12:00:00+08:00" 3)
[ "$RC" = "200" ] && ok "11:00 起的下一场能订上（紧邻不算冲突）" || bad "紧邻时段被误判为冲突（HTTP ${RC}）"
RC=$(book seed-user-101 "部分重叠" "2026-10-01T10:30:00+08:00" "2026-10-01T11:30:00+08:00" 3)
[ "$RC" = "409" ] && ok "部分重叠被拒" || bad "部分重叠没被拒（HTTP ${RC}）"
RC=$(book seed-user-102 "超容量" "2026-10-02T10:00:00+08:00" "2026-10-02T11:00:00+08:00" 999)
[ "$RC" = "400" ] && ok "超会议室容量被拒" || bad "超容量没被拒（HTTP ${RC}）"

step "6. 取消即释放时间段"
BID=$(psq "SELECT id FROM oa_admin.room_booking WHERE room_id=${ROOM} AND status='BOOKED' AND lower(during)='2026-10-01 10:00:00+08' LIMIT 1")
OWNER=$(psq "SELECT booker_id FROM oa_admin.room_booking WHERE id=${BID}")
[ "$(http seed-user-199 -X POST "$BASE/admin-biz/rooms/bookings/${BID}/cancel")" != "200" ] \
  && ok "别人取消不了你的预定" || bad "越权取消成功了"
as "$OWNER" -X POST "$BASE/admin-biz/rooms/bookings/${BID}/cancel" >/dev/null
RC=$(book seed-user-103 "捡漏" "${SS}" "${SE}" 2)
[ "$RC" = "200" ] && ok "取消后时段立即可再订（约束带 WHERE status='BOOKED'，改状态即让出）" \
  || bad "取消后时段没释放（HTTP ${RC}）"

step "7. 车辆预定：与会议室同构"
VID=$(psq "SELECT id FROM oa_admin.vehicle ORDER BY id LIMIT 1")
bookcar() { http "$1" -X POST "$BASE/admin-biz/vehicles/bookings" -H 'Content-Type: application/json' \
             -d "{\"vehicleId\":${VID},\"purpose\":\"$2\",\"startAt\":\"$3\",\"endAt\":\"$4\"}"; }
RC=$(bookcar seed-user-100 "接机" "2026-10-05T08:00:00+08:00" "2026-10-05T12:00:00+08:00")
[ "$RC" = "200" ] && ok "用车预定成功" || bad "用车预定失败（HTTP ${RC}）"
RC=$(bookcar seed-user-101 "抢车" "2026-10-05T10:00:00+08:00" "2026-10-05T14:00:00+08:00")
[ "$RC" = "409" ] && ok "同车重叠时段被拒（同一套排他约束，没有第二份实现）" || bad "车辆冲突没被拒（HTTP ${RC}）"

step "8. 用品库存：条件更新不超发"
SUP=$(psq "SELECT id FROM oa_admin.supply WHERE code='SUP-A4'")
psq "UPDATE oa_admin.supply SET stock = 10 WHERE id=${SUP}" >/dev/null
# 50 个人每人领 1，只有 10 个能成
R=$(seq 100 149 | xargs -P 50 -I{} curl -s -o /dev/null -w "%{http_code}\n" \
     -H "X-OA-User: seed-user-{}" -X POST "$BASE/admin-biz/supplies/requests" -H 'Content-Type: application/json' \
     -d "{\"supplyId\":${SUP},\"qty\":1}")
S=$(echo "$R" | grep -c '^200$')
[ "$S" = "10" ] && ok "50 人抢 10 件库存，恰好 10 人成功" || bad "成功数应为 10，实为 ${S}"
[ "$(psq "SELECT stock FROM oa_admin.supply WHERE id=${SUP}")" = "0" ] \
  && ok "库存归零、未被扣成负数（CHECK stock >= 0 是最后一道）" || bad "库存不对"

step "9. 资产领用：行锁 + 状态机"
psq "INSERT INTO oa_admin.asset(asset_no,name,category,status) VALUES ('A-0001','ThinkPad X1','LAPTOP','IDLE')" >/dev/null
AID=$(psq "SELECT id FROM oa_admin.asset WHERE asset_no='A-0001'")
R=$(seq 100 129 | xargs -P 30 -I{} curl -s -o /dev/null -w "%{http_code}\n" \
     -H "X-OA-User: seed-user-{}" -X POST "$BASE/admin-biz/assets/claim" -H 'Content-Type: application/json' \
     -d "{\"assetId\":${AID}}")
[ "$(echo "$R" | grep -c '^200$')" = "1" ] && ok "30 人抢 1 台设备，只有 1 人领到" || bad "资产被重复领用"
[ "$(psq "SELECT status FROM oa_admin.asset WHERE id=${AID}")" = "IN_USE" ] \
  && ok "资产状态 IN_USE 且持有人非空（CHECK 约束保证自洽）" || bad "资产状态不对"
HOLDER=$(psq "SELECT holder_id FROM oa_admin.asset WHERE id=${AID}")
[ "$(http seed-user-199 -X POST "$BASE/admin-biz/assets/${AID}/return")" != "200" ] \
  && ok "别人还不了你名下的资产" || bad "越权归还成功了"
as "$HOLDER" -X POST "$BASE/admin-biz/assets/${AID}/return" >/dev/null
[ "$(psq "SELECT status FROM oa_admin.asset WHERE id=${AID}")" = "IDLE" ] && ok "归还后回到 IDLE" || bad "归还失败"

step "10. 公文：文号在核发时生成且年内连续"
D1=$(as "$ADMIN" -X POST "$BASE/doc/official" -H 'Content-Type: application/json' -d '{"direction":"OUT","title":"关于系统升级的通知","body":"周六维护"}' | jq -r '.data.id')
D2=$(as "$ADMIN" -X POST "$BASE/doc/official" -H 'Content-Type: application/json' -d '{"direction":"OUT","title":"关于国庆放假的通知","body":"放假七天"}' | jq -r '.data.id')
[ -z "$(psq "SELECT doc_number FROM oa_doc.official_doc WHERE id=${D1}")" ] \
  && ok "拟稿阶段【没有】文号（撤销的稿子不该占号，年度序列不能有空洞）" || bad "拟稿就给了文号"
N1=$(as "$ADMIN" -X POST "$BASE/doc/official/${D1}/issue" | jq -r '.data.docNumber')
N2=$(as "$ADMIN" -X POST "$BASE/doc/official/${D2}/issue" | jq -r '.data.docNumber')
SEQ1=$(echo "$N1" | grep -oE '[0-9]+ 号' | grep -oE '[0-9]+')
SEQ2=$(echo "$N2" | grep -oE '[0-9]+ 号' | grep -oE '[0-9]+')
[ -n "$SEQ1" ] && [ "$((SEQ2 - SEQ1))" = "1" ] && ok "文号连续：${N1} → ${N2}" || bad "文号不连续：${N1} / ${N2}"
[ "$(http "$ADMIN" -X POST "$BASE/doc/official/${D1}/issue")" != "200" ] && ok "已核发不能重复核发" || bad "重复核发成功了"
D3=$(as "$ADMIN" -X POST "$BASE/doc/official" -H 'Content-Type: application/json' -d '{"direction":"IN","title":"上级来文","sourceOrg":"总部"}' | jq -r '.data.id')
[ "$(http "$ADMIN" -X POST "$BASE/doc/official/${D3}/archive")" != "200" ] \
  && ok "未核发不能归档" || bad "未核发也能归档"
[ "$(http seed-user-100 -X POST "$BASE/doc/official" -H 'Content-Type: application/json' -d '{"direction":"OUT","title":"x"}')" = "403" ] \
  && ok "普通员工不能拟稿" || bad "普通员工能拟稿"

step "11. 中文检索必须真的能搜到"
# tsvector('simple') 切不了中文：整句是一个 token，搜"放假"永远 0 行，
# 而接口返回 200 + 空数组，看起来像"确实没这份文件"。已改 pg_trgm + ILIKE（V62）。
HIT=$(curl -s -G -H "X-OA-User: $ADMIN" "$BASE/doc/official" --data-urlencode "keyword=放假" | jq -r '.data | length')
[ "$HIT" = "1" ] && ok "中文关键词命中 1 条（pg_trgm，不是形同虚设的 tsvector）" || bad "中文检索命中 ${HIT} 条"
MISS=$(curl -s -G -H "X-OA-User: $ADMIN" "$BASE/doc/official" --data-urlencode "keyword=不存在的词" | jq -r '.data | length')
[ "$MISS" = "0" ] && ok "不该命中的词返回 0 条" || bad "检索有误报"

step "12. 知识库：接口权限之外还有对象级权限"
[ "$(as "$ADMIN" "$BASE/doc/kb/authorizer" | jq -r '.data.impl')" = "LOCAL(kb_share)" ] \
  && ok "当前走内置 kb_share（SpiceDB 开关默认关，回退路径是完整可用的）" || bad "授权实现不对"
KB=$(as "$ADMIN" -X POST "$BASE/doc/kb" -H 'Content-Type: application/json' \
     -d '{"folderId":1,"title":"薪酬调整方案","summary":"内部","body":"机密内容"}' | jq -r '.data.id')
[ "$(http "$ADMIN" "$BASE/doc/kb/${KB}")" = "200" ] && ok "owner 能读自己的文档" || bad "owner 读不了"
[ "$(http seed-user-100 "$BASE/doc/kb/${KB}")" = "403" ] \
  && ok "★ 有 oa:kb:read 接口权限、但没有对象权限 → 403（IDOR 被堵住）" || bad "IDOR：别人能读私有文档"
[ "$(as "$ADMIN" "$BASE/doc/kb/${KB}/explain?userId=seed-user-100" | jq -r '.data.allowed')" = "false" ] \
  && ok "解释器说明了为什么不能看" || bad "解释器结果不对"
as "$ADMIN" -X POST "$BASE/doc/kb/share" -H 'Content-Type: application/json' \
  -d "{\"resourceType\":\"DOC\",\"resourceId\":${KB},\"subjectType\":\"USER\",\"subjectId\":\"seed-user-100\",\"level\":\"READ\"}" >/dev/null
[ "$(http seed-user-100 "$BASE/doc/kb/${KB}")" = "200" ] && ok "共享后可读" || bad "共享后仍读不了"
[ "$(http seed-user-100 -X PUT "$BASE/doc/kb/${KB}" -H 'Content-Type: application/json' -d '{"title":"篡改","body":"x"}')" = "403" ] \
  && ok "只读共享不能改（READ ≠ WRITE）" || bad "只读者改成功了"

step "13. 知识库 ORG 共享按 org_path 前缀继承"
TARGET_ORG=$(psq "SELECT o.id FROM oa_org.org_unit o
                   JOIN oa_org.employee_org_assignment a ON a.org_unit_id = o.id AND a.valid_to IS NULL
                   JOIN oa_org.employee e ON e.id = a.employee_id AND e.user_id = 'seed-user-150'
                  LIMIT 1")
if [ -n "$TARGET_ORG" ]; then
  PARENT=$(psq "SELECT parent_id FROM oa_org.org_unit WHERE id=${TARGET_ORG}")
  SHARE_ORG=${PARENT:-$TARGET_ORG}
  [ "$(http seed-user-150 "$BASE/doc/kb/${KB}")" = "403" ] && ok "共享前该员工读不到" || bad "共享前就能读"
  as "$ADMIN" -X POST "$BASE/doc/kb/share" -H 'Content-Type: application/json' \
    -d "{\"resourceType\":\"DOC\",\"resourceId\":${KB},\"subjectType\":\"ORG\",\"subjectId\":\"${SHARE_ORG}\",\"level\":\"READ\"}" >/dev/null
  [ "$(http seed-user-150 "$BASE/doc/kb/${KB}")" = "200" ] \
    && ok "授权给【上级部门】，子部门员工按 org_path 前缀继承到（不展开 id 列表）" \
    || bad "ORG 共享没有向下继承"
else
  bad "找不到测试用组织"
fi

step "14. 访客手机号必须是密文列"
as seed-user-100 -X POST "$BASE/admin-biz/visitors" -H 'Content-Type: application/json' \
  -d '{"name":"张访客","phone":"13800138000","company":"某公司","visitAt":"2026-10-10T09:00:00+08:00"}' >/dev/null
[ -z "$(psq "SELECT 1 FROM oa_admin.visitor WHERE phone_enc::text LIKE '%13800138000%'")" ] \
  && ok "手机号未以明文落库" || bad "访客手机号是明文"
[ -n "$(psq "SELECT phone_hash FROM oa_admin.visitor LIMIT 1")" ] \
  && ok "有 HMAC 检索列（密文列不能等值查，检索靠它）" || bad "缺少 phone_hash"
[ -z "$(as seed-user-100 "$BASE/admin-biz/visitors" | grep -o '13800138000')" ] \
  && ok "列表接口不返回手机号（少一个泄露面）" || bad "列表返回了手机号"

echo; echo "════════════════════════════════════════"
echo "  Phase 7 文档域 + 行政域冒烟：通过 ${PASS} / 失败 ${FAIL}"
echo "════════════════════════════════════════"
[ "$FAIL" -eq 0 ] || exit 1
