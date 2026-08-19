#!/usr/bin/env bash
# Phase 4 审批底座冒烟：发件箱 → Kafka、审批人由汇报线算出、待办读模型、额度冻结/消耗/释放与幂等。
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

step "1. 构建并启动（本地流程替身模式）"
mvn -B -q -f "$ROOT/pom.xml" clean install -DskipTests >/tmp/oa-p4-build.log 2>&1 \
  && ok "构建通过" || { bad "构建失败"; tail -25 /tmp/oa-p4-build.log; exit 1; }
pkill -f 'oa-app-.*\.jar' 2>/dev/null; sleep 1
JAR=$(ls "$ROOT"/oa-app/target/oa-app-*.jar | grep -v original | head -1)
OA_ORG_SEED_ENABLED=true OA_BOOTSTRAP_ADMIN="$ADMIN" OA_WORKFLOW_MODE=LOCAL \
  java -jar "$JAR" >/tmp/oa-app-phase4.log 2>&1 &
APP_PID=$!
cleanup() { kill $APP_PID 2>/dev/null; wait $APP_PID 2>/dev/null; }
trap cleanup EXIT
for i in $(seq 1 45); do curl -sf "$BASE/system/ping" >/dev/null 2>&1 && break; sleep 2; done
for i in $(seq 1 20); do [ "$(rc "$ADMIN" "$BASE/iam/admin/cache-stats")" = "0" ] && break; sleep 1; done
[ "$(rc "$ADMIN" "$BASE/iam/admin/cache-stats")" = "0" ] && ok "应用就绪" || { bad "启动失败"; tail -40 /tmp/oa-app-phase4.log; exit 1; }

step "2. 装载数据并准备实验对象"
as "$ADMIN" -X DELETE "$BASE/org/seed" >/dev/null 2>&1
psq "DELETE FROM oa_iam.grant_record WHERE subject_id LIKE 'seed-user-%' AND subject_id <> '${ADMIN}'" >/dev/null
psq "DELETE FROM oa_flow.leave_balance_txn; DELETE FROM oa_flow.leave_request; DELETE FROM oa_flow.leave_balance" >/dev/null
psq "DELETE FROM oa_flow.todo_item; DELETE FROM oa_flow.approval_node_log; DELETE FROM oa_flow.approval_instance; DELETE FROM oa_flow.oa_outbox" >/dev/null
SEED=$(as "$ADMIN" -X POST "$BASE/org/seed?orgs=500&employees=2000")
[ "$(echo "$SEED" | jq -r '.data.employees // 0')" = "2000" ] && ok "2,000 员工就位" || { bad "装载失败: $SEED"; exit 1; }
sleep 2

# 取一个有实线上级的普通员工
# 必须挑汇报链【至少两级】的人：链只有一级的话，"天数决定级数"这条根本测不出来
U=$(psq "SELECT 'seed-user-'||e.id FROM oa_org.employee e
           JOIN oa_org.reporting_line r1 ON r1.employee_id=e.id AND r1.valid_to IS NULL
           JOIN oa_org.reporting_line r2 ON r2.employee_id=r1.manager_employee_id AND r2.valid_to IS NULL
           JOIN oa_org.employee_org_assignment a ON a.employee_id=e.id AND a.valid_to IS NULL AND a.is_leader=false
          ORDER BY e.id LIMIT 1")
MGR=$(psq "SELECT 'seed-user-'||r.manager_employee_id FROM oa_org.reporting_line r JOIN oa_org.employee e ON e.id=r.employee_id WHERE e.user_id='${U}' AND r.valid_to IS NULL LIMIT 1")
R_EMP=$(psq "SELECT id FROM oa_iam.role WHERE code='EMPLOYEE'")
for who in "$U" "$MGR"; do
  as "$ADMIN" -X POST "$BASE/iam/grants" -H 'Content-Type: application/json' \
    -d "{\"subjectType\":\"USER\",\"subjectId\":\"${who}\",\"roleId\":${R_EMP},\"scopeType\":\"SELF\"}" >/dev/null
done
echo "     申请人 ${U}，实线上级 ${MGR}"
YEAR=$(date +%Y)
as "$ADMIN" -X POST "$BASE/flow/leave/balances/grant" -H 'Content-Type: application/json' \
  -d "{\"userId\":\"${U}\",\"leaveTypeCode\":\"ANNUAL\",\"period\":\"${YEAR}\",\"days\":10}" >/dev/null
AV=$(as "$U" "$BASE/flow/leave/balances" | jq -r '.data[] | select(.leaveTypeCode=="ANNUAL") | .availableDays')
[ "$AV" = "10.0" ] || [ "$AV" = "10" ] && ok "年假额度发放 10 天（可用 ${AV}）" || bad "额度发放异常: ${AV}"

step "3. 提单：审批人由汇报线动态算出（ADR-0010）"
SUB=$(as "$U" -X POST "$BASE/flow/leave" -H 'Content-Type: application/json' \
      -d "{\"leaveTypeCode\":\"ANNUAL\",\"startDate\":\"${YEAR}-09-01\",\"endDate\":\"${YEAR}-09-02\",\"days\":2,\"reason\":\"smoke\"}")
NO=$(echo "$SUB" | jq -r '.data.requestNo // ""')
CHAIN=$(echo "$SUB" | jq -r '.data.approverChain | join(",")')
[ -n "$NO" ] && ok "请假单 ${NO} 已提交" || { bad "提单失败: $SUB"; exit 1; }
[ "$CHAIN" = "$MGR" ] && ok "审批人 = 实线上级 ${MGR}（2 天 → 1 级）" || bad "审批链 ${CHAIN} ≠ 期望 ${MGR}"

FROZEN=$(as "$U" "$BASE/flow/leave/balances" | jq -r '.data[] | select(.leaveTypeCode=="ANNUAL") | .frozenDays')
[ "${FROZEN%.*}" = "2" ] && ok "额度已冻结 2 天（防连提两单用超）" || bad "冻结异常: ${FROZEN}"

step "4. 事务发件箱 → Kafka"
OUT=$(psq "SELECT status||':'||topic FROM oa_flow.oa_outbox ORDER BY id DESC LIMIT 1")
echo "     发件箱最新一条: ${OUT}"
sleep 3
SENT=$(psq "SELECT count(*) FROM oa_flow.oa_outbox WHERE status='SENT' AND topic='workflow.command.start.v1'")
[ "$SENT" -ge 1 ] 2>/dev/null && ok "StartProcessCommandV1 已投递到 Kafka（${SENT} 条）" || bad "发件箱未投递成功"
PAYLOAD=$(psq "SELECT payload FROM oa_flow.oa_outbox ORDER BY id DESC LIMIT 1")
echo "$PAYLOAD" | grep -q '"approverChain"' && ok "消息里带着 OA 算好的 approverChain（中台无需反查 OA）" || bad "消息缺 approverChain"
echo "$PAYLOAD" | grep -q '"eventId"' && ok "消息符合中台 EventEnvelopeV1 信封契约" || bad "消息信封不符合契约"

step "5. 待办读模型"
TODO=$(as "$MGR" "$BASE/flow/todos" | jq -r '.data | length')
[ "$TODO" = "1" ] && ok "审批人看到 1 条待办" || bad "审批人待办数 = ${TODO}"
TASK=$(as "$MGR" "$BASE/flow/todos" | jq -r '.data[0].taskId')
TITLE=$(as "$MGR" "$BASE/flow/todos" | jq -r '.data[0].title')
echo "     待办: ${TITLE}"
[ "$(as "$U" "$BASE/flow/todos" | jq -r '.data | length')" = "0" ] && ok "申请人自己看不到这条待办" || bad "申请人不该看到自己的待办"

step "6. 审批通过 → 额度消耗"
[ "$(as "$MGR" -X POST "$BASE/flow/todos/${TASK}/complete" -H 'Content-Type: application/json' -d '{"outcome":"APPROVE","comment":"ok"}' | jq -r '.code')" = "0" ] \
  && ok "审批通过" || bad "办理失败"
sleep 1
ST=$(as "$U" "$BASE/flow/leave/${NO}" | jq -r '.data.status')
[ "$ST" = "APPROVED" ] && ok "请假单状态 → APPROVED" || bad "单据状态 = ${ST}"
BAL=$(as "$U" "$BASE/flow/leave/balances" | jq -c '.data[] | select(.leaveTypeCode=="ANNUAL") | {used:.usedDays,frozen:.frozenDays,avail:.availableDays}')
echo "     额度: ${BAL}"
echo "$BAL" | grep -q '"used":2' && echo "$BAL" | grep -q '"frozen":0' \
  && ok "冻结转消耗：used=2 frozen=0 available=8" || bad "额度结算异常: ${BAL}"
[ "$(as "$MGR" "$BASE/flow/todos" | jq -r '.data | length')" = "0" ] && ok "待办已从工作台消失" || bad "待办未关闭"

step "7. 额度扣减幂等（重放同一单据不会扣两次）"
BID=$(psq "SELECT id FROM oa_flow.leave_balance WHERE user_id='${U}' LIMIT 1")
# ⚠️ 不能用 `RETURNING id` 是否为空来判断：psql 在无返回行时仍会把命令标签
# `INSERT 0 0` 打到 stdout，看起来永远"有输出"。直接数行数最不会骗人。
psq "INSERT INTO oa_flow.leave_balance_txn(balance_id,request_id,action,days) VALUES (${BID},'${NO}','CONSUME',2) ON CONFLICT DO NOTHING" >/dev/null
CNT=$(psq "SELECT count(*) FROM oa_flow.leave_balance_txn WHERE request_id='${NO}' AND action='CONSUME'")
[ "$CNT" = "1" ] && ok "重复 CONSUME 被唯一约束挡下，仍只有 1 条流水（幂等不靠应用层记得判重）" || bad "幂等失效，流水变成 ${CNT} 条"
USED_AFTER=$(as "$U" "$BASE/flow/leave/balances" | jq -r '.data[] | select(.leaveTypeCode=="ANNUAL") | .usedDays')
[ "${USED_AFTER%.*}" = "2" ] && ok "已用天数没有被重放扣成 4 天" || bad "重放导致重复扣减: ${USED_AFTER}"

step "8. 驳回 → 额度释放"
SUB2=$(as "$U" -X POST "$BASE/flow/leave" -H 'Content-Type: application/json' \
       -d "{\"leaveTypeCode\":\"ANNUAL\",\"startDate\":\"${YEAR}-10-01\",\"endDate\":\"${YEAR}-10-03\",\"days\":3,\"reason\":\"smoke reject\"}")
NO2=$(echo "$SUB2" | jq -r '.data.requestNo')
TASK2=$(as "$MGR" "$BASE/flow/todos" | jq -r '.data[0].taskId')
as "$MGR" -X POST "$BASE/flow/todos/${TASK2}/complete" -H 'Content-Type: application/json' -d '{"outcome":"REJECT","comment":"no"}' >/dev/null
sleep 1
[ "$(as "$U" "$BASE/flow/leave/${NO2}" | jq -r '.data.status')" = "REJECTED" ] && ok "单据 → REJECTED" || bad "驳回未生效"
BAL2=$(as "$U" "$BASE/flow/leave/balances" | jq -c '.data[] | select(.leaveTypeCode=="ANNUAL") | {used:.usedDays,frozen:.frozenDays,avail:.availableDays}')
echo "$BAL2" | grep -q '"frozen":0' && echo "$BAL2" | grep -q '"used":2' \
  && ok "驳回后冻结释放，已用未变（${BAL2}）" || bad "驳回额度未正确释放: ${BAL2}"

step "9. 额度不足直接拒绝"
FAILRC=$(as "$U" -X POST "$BASE/flow/leave" -H 'Content-Type: application/json' \
         -d "{\"leaveTypeCode\":\"ANNUAL\",\"startDate\":\"${YEAR}-11-01\",\"endDate\":\"${YEAR}-11-30\",\"days\":12,\"reason\":\"too long\"}" | jq -r '.code')
[ "$FAILRC" = "5002" ] && ok "超出可用额度被拒（5002）" || bad "额度校验失效: ${FAILRC}"

step "10. 多级审批：天数决定级数"
SUB3=$(as "$U" -X POST "$BASE/flow/leave" -H 'Content-Type: application/json' \
       -d "{\"leaveTypeCode\":\"ANNUAL\",\"startDate\":\"${YEAR}-12-01\",\"endDate\":\"${YEAR}-12-05\",\"days\":5,\"reason\":\"two levels\"}")
LEVELS=$(echo "$SUB3" | jq -r '.data.approverChain | length')
[ "$LEVELS" -ge 2 ] 2>/dev/null && ok "5 天 → ${LEVELS} 级审批（量级驱动级数）" || bad "级数 = ${LEVELS}，期望 ≥2"

step "11. 单据的组织快照（ADR-0007）"
SNAP=$(psq "SELECT org_path FROM oa_flow.approval_instance WHERE business_key='${NO}'")
UPATH=$(psq "SELECT o.path FROM oa_org.org_unit o JOIN oa_org.employee_org_assignment a ON a.org_unit_id=o.id JOIN oa_org.employee e ON e.id=a.employee_id WHERE e.user_id='${U}' AND a.valid_to IS NULL LIMIT 1")
[ "$SNAP" = "$UPATH" ] && ok "审批单冗余了发生时的 org_path = ${SNAP}" || bad "org_path 快照缺失: ${SNAP}"
IDX=$(docker exec oa-postgres psql -U "$PG_USER" -d "$PG_DB" -tAc "SELECT indexdef FROM pg_indexes WHERE indexname='ix_ai_org_path'" 2>/dev/null)
echo "$IDX" | grep -q "text_pattern_ops" && ok "该列索引是 text_pattern_ops（前缀匹配可用）" || bad "索引 opclass 不对"

step "12. 运维视图"
STATUS=$(as "$ADMIN" "$BASE/flow/admin/status" | jq -c '.data')
echo "     ${STATUS}"
echo "$STATUS" | grep -q '"dead":0' && ok "发件箱无死信" || bad "发件箱有死信"

echo; echo "════════════════════════════════════"
echo "  Phase 4 审批底座冒烟：通过 $PASS / 失败 $FAIL"
echo "════════════════════════════════════"
[ "$FAIL" -eq 0 ] || exit 1
