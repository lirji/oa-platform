#!/usr/bin/env bash
# Phase 4b 冒烟：接【真 workflow-platform】跑通通用审批。
#
# 与 phase4-flow-smoke.sh 的分工：
#   phase4  用本地替身，验 OA 自己的逻辑（审批人计算、发件箱、额度、幂等）——不依赖外部进程；
#   phase4b 用真中台，验【集成】——BPMN 部署、命令跨总线、多实例串行推进、回绑、结束检测。
#
# 前置：workflow-platform 的 compose 在跑（:8300 + :29092），OA 的 compose 在跑（:35432 等）。
# ⚠️ 本脚本会占用 8400，若 oa-app 容器在跑会先把它停掉，结束时不自动拉回（避免掩盖端口冲突）。
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DEPLOY="$ROOT/deploy"
BASE="http://localhost:8400/api/v1"
WF="http://localhost:8300"
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
wf()  { curl -s -H "X-Workflow-Tenant: oa" "$@"; }

step "0. 中台可达性（不可达就没必要往下跑）"
[ "$(curl -s -o /dev/null -w '%{http_code}' $WF/actuator/health)" = "200" ] \
  && ok "workflow-platform :8300 健康" || { bad "中台不可达，先起 workflow-platform 的 compose"; exit 1; }
# 中台必须已有通用 completeTask 端点。用一个不存在的 taskId 探：
# 路由不存在 → Spring 的默认 404 JSON（有 "path" 字段）；路由存在 → 领域 404（有 "任务不存在"）。
PROBE=$(curl -s -X POST "$WF/api/v1/tasks/__probe__/complete" -H 'X-Workflow-Tenant: oa' \
        -H 'Content-Type: application/json' -d '{"outcome":"APPROVE"}')
echo "$PROBE" | grep -q "任务不存在" \
  && ok "通用 completeTask 端点已就绪" || { bad "中台缺 completeTask 端点（响应：$PROBE）"; exit 1; }

step "1. 构建并以 REMOTE 模式启动"
mvn -B -q -f "$ROOT/pom.xml" clean install -DskipTests >/tmp/oa-p4b-build.log 2>&1 \
  && ok "构建通过" || { bad "构建失败"; tail -25 /tmp/oa-p4b-build.log; exit 1; }
docker stop oa-app >/dev/null 2>&1
pkill -f 'oa-app-.*\.jar' 2>/dev/null; sleep 2
JAR=$(ls "$ROOT"/oa-app/target/oa-app-*.jar | grep -v original | head -1)
OA_ORG_SEED_ENABLED=true OA_BOOTSTRAP_ADMIN="$ADMIN" \
OA_WORKFLOW_MODE=REMOTE OA_WORKFLOW_URL="$WF" OA_WORKFLOW_KAFKA=localhost:29092 \
  java -jar "$JAR" >/tmp/oa-app-phase4b.log 2>&1 &
APP_PID=$!
cleanup() { kill $APP_PID 2>/dev/null; wait $APP_PID 2>/dev/null; }
trap cleanup EXIT
for i in $(seq 1 45); do curl -sf "$BASE/system/ping" >/dev/null 2>&1 && break; sleep 2; done
curl -sf "$BASE/system/ping" >/dev/null 2>&1 && ok "应用就绪（REMOTE）" || { bad "启动失败"; tail -40 /tmp/oa-app-phase4b.log; exit 1; }
# 网关必须真的握着 RemoteWorkflowClient。SDK 默认 enabled=false 会注入 Noop：
# 查询返回空列表且不报错，症状是"待办永远不出现"而日志一片干净。
grep -q "client=RemoteWorkflowClient" /tmp/oa-app-phase4b.log \
  && ok "SDK 客户端是 Remote（不是 Noop）" || bad "网关握的是 Noop 客户端，REMOTE 名存实亡"
grep -qE "BPMN oaGenericApproval (已部署到中台|已在中台就绪)" /tmp/oa-app-phase4b.log \
  && ok "oa-generic-approval-v1 BPMN 在中台就绪" || bad "BPMN 未就绪"

step "2. 装载组织并挑出两级汇报链"
as "$ADMIN" -X DELETE "$BASE/org/seed" >/dev/null 2>&1
psq "DELETE FROM oa_flow.leave_balance_txn; DELETE FROM oa_flow.leave_request; DELETE FROM oa_flow.leave_balance" >/dev/null
psq "DELETE FROM oa_flow.todo_item; DELETE FROM oa_flow.approval_node_log; DELETE FROM oa_flow.approval_instance; DELETE FROM oa_flow.oa_outbox" >/dev/null
SEED=$(as "$ADMIN" -X POST "$BASE/org/seed?orgs=200&employees=800")
[ "$(echo "$SEED" | jq -r '.data.employees // 0')" = "800" ] && ok "800 员工就位" || { bad "装载失败：$SEED"; exit 1; }
sleep 2
U=$(psq "SELECT 'seed-user-'||e.id FROM oa_org.employee e
   JOIN oa_org.reporting_line r1 ON r1.employee_id=e.id AND r1.valid_to IS NULL
   JOIN oa_org.reporting_line r2 ON r2.employee_id=r1.manager_employee_id AND r2.valid_to IS NULL
   JOIN oa_org.employee_org_assignment a ON a.employee_id=e.id AND a.valid_to IS NULL AND a.is_leader=false
   ORDER BY e.id LIMIT 1")
M1=$(psq "SELECT 'seed-user-'||r.manager_employee_id FROM oa_org.reporting_line r JOIN oa_org.employee e ON e.id=r.employee_id WHERE e.user_id='${U}' AND r.valid_to IS NULL LIMIT 1")
M2=$(psq "SELECT 'seed-user-'||r2.manager_employee_id FROM oa_org.reporting_line r1 JOIN oa_org.employee e ON e.id=r1.employee_id JOIN oa_org.reporting_line r2 ON r2.employee_id=r1.manager_employee_id AND r2.valid_to IS NULL WHERE e.user_id='${U}' AND r1.valid_to IS NULL LIMIT 1")
[ -n "$U" ] && [ -n "$M1" ] && [ -n "$M2" ] && ok "申请人 ${U} / 一级 ${M1} / 二级 ${M2}" || { bad "挑不出两级汇报链"; exit 1; }
R_EMP=$(psq "SELECT id FROM oa_iam.role WHERE code='EMPLOYEE'")
for who in "$U" "$M1" "$M2"; do
  as "$ADMIN" -X POST "$BASE/iam/grants" -H 'Content-Type: application/json' \
    -d "{\"subjectType\":\"USER\",\"subjectId\":\"${who}\",\"roleId\":${R_EMP},\"scopeType\":\"SELF\"}" >/dev/null
done
YEAR=$(date +%Y)
as "$ADMIN" -X POST "$BASE/flow/leave/balances/grant" -H 'Content-Type: application/json' \
  -d "{\"userId\":\"${U}\",\"leaveTypeCode\":\"ANNUAL\",\"period\":\"${YEAR}\",\"days\":10}" >/dev/null

step "3. 提单 → 命令必须跨到【中台的】Kafka 并把流程起起来"
SUB=$(as "$U" -X POST "$BASE/flow/leave" -H 'Content-Type: application/json' \
  -d "{\"leaveTypeCode\":\"ANNUAL\",\"startDate\":\"${YEAR}-12-01\",\"endDate\":\"${YEAR}-12-05\",\"days\":5,\"reason\":\"phase4b\"}")
NO=$(echo "$SUB" | jq -r '.data.requestNo')
CHAIN=$(echo "$SUB" | jq -r '.data.approverChain | length')
[ "$CHAIN" = "2" ] && ok "5 天 → 审批链 2 级（量级驱动级数）" || bad "审批链级数应为 2，实为 ${CHAIN}"
for i in $(seq 1 20); do
  [ "$(psq "SELECT status FROM oa_flow.oa_outbox WHERE topic='workflow.command.start.v1' ORDER BY id DESC LIMIT 1")" = "SENT" ] && break; sleep 1
done
[ "$(psq "SELECT status FROM oa_flow.oa_outbox WHERE topic='workflow.command.start.v1' ORDER BY id DESC LIMIT 1")" = "SENT" ] \
  && ok "发件箱已投递 start 命令" || bad "start 命令未投出"
for i in $(seq 1 20); do
  [ "$(wf "$WF/api/v1/process-instances?definitionKey=oaGenericApproval&businessKey=${NO}" | jq -r 'length')" = "1" ] && break; sleep 1
done
PI=$(wf "$WF/api/v1/process-instances?definitionKey=oaGenericApproval&businessKey=${NO}" | jq -r '.[0].processInstanceId // empty')
[ -n "$PI" ] && ok "中台已起流程 ${PI:0:12}…（命令确实到了中台那条总线）" || { bad "中台没有该 businessKey 的流程"; exit 1; }

step "4. 待办投影：审批人看得到，申请人看不到"
for i in $(seq 1 30); do
  [ "$(as "$M1" "$BASE/flow/todos" | jq -r '.data | length')" = "1" ] && break; sleep 2
done
[ "$(as "$M1" "$BASE/flow/todos" | jq -r '.data | length')" = "1" ] && ok "一级审批人有 1 条待办" || bad "一级审批人没有待办"
[ "$(as "$U"  "$BASE/flow/todos" | jq -r '.data | length')" = "0" ] && ok "申请人看不到自己的单子" || bad "申请人不该有待办"
[ -n "$(psq "SELECT process_instance_id FROM oa_flow.approval_instance WHERE business_key='${NO}'")" ] \
  && ok "processInstanceId 已回绑（发起是异步的，靠对账补）" || bad "processInstanceId 未回绑"

step "5. 逐级办理：多实例串行推进"
T1=$(as "$M1" "$BASE/flow/todos" | jq -r '.data[0].taskId')
as "$M1" -X POST "$BASE/flow/todos/$T1/complete" -H 'Content-Type: application/json' \
  -d '{"outcome":"APPROVE","comment":"一级同意"}' >/dev/null
sleep 2
NEXT=$(wf "$WF/api/v1/tasks?businessKey=${NO}" | jq -r '.[0].assignee // empty')
[ "$NEXT" = "$M2" ] && ok "一级通过后中台把任务交给二级 ${M2}" || bad "下一级应为 ${M2}，实为 ${NEXT}"
[ "$(as "$M2" "$BASE/flow/todos" | jq -r '.data | length')" = "1" ] \
  && ok "二级待办【即时】出现（不等 60 秒对账）" || bad "二级待办没有即时出现"
[ "$(as "$M1" "$BASE/flow/todos" | jq -r '.data | length')" = "0" ] && ok "一级待办已消失" || bad "一级待办没消失"

T2=$(as "$M2" "$BASE/flow/todos" | jq -r '.data[0].taskId')
as "$M2" -X POST "$BASE/flow/todos/$T2/complete" -H 'Content-Type: application/json' \
  -d '{"outcome":"APPROVE","comment":"二级同意"}' >/dev/null
sleep 2

step "6. 收尾：中台结束 → OA 单据、额度、待办同步收敛"
RUNNING=$(wf "$WF/api/v1/process-instances?definitionKey=oaGenericApproval&businessKey=${NO}" | jq -r '.[0].running')
[ "$RUNNING" = "false" ] && ok "中台流程已结束（phase=COMPLETED）" || bad "中台流程仍在运行"
[ "$(psq "SELECT status||'/'||outcome FROM oa_flow.approval_instance WHERE business_key='${NO}'")" = "FINISHED/APPROVED" ] \
  && ok "OA 审批实例 FINISHED/APPROVED" || bad "OA 实例状态不对：$(psq "SELECT status||'/'||coalesce(outcome,'∅') FROM oa_flow.approval_instance WHERE business_key='${NO}'")"
[ "$(psq "SELECT status FROM oa_flow.leave_request WHERE request_no='${NO}'")" = "APPROVED" ] \
  && ok "请假单 APPROVED" || bad "请假单状态不对"
BAL=$(psq "SELECT used_days||'/'||frozen_days FROM oa_flow.leave_balance WHERE user_id='${U}'")
[ "$BAL" = "5.0/0.0" ] && ok "额度闭环：已用 5 天、冻结归零" || bad "额度应为 5.0/0.0，实为 ${BAL}"
[ "$(as "$M2" "$BASE/flow/todos" | jq -r '.data | length')" = "0" ] && ok "待办清空" || bad "待办没清空"
[ "$(psq "SELECT count(*) FROM oa_flow.approval_node_log")" = "2" ] \
  && ok "审批轨迹 2 条（逐级留痕）" || bad "轨迹条数不对"

step "7. 驳回路径：任一环节 REJECT 立刻终止，冻结额度必须释放"
SUB2=$(as "$U" -X POST "$BASE/flow/leave" -H 'Content-Type: application/json' \
  -d "{\"leaveTypeCode\":\"ANNUAL\",\"startDate\":\"${YEAR}-12-10\",\"endDate\":\"${YEAR}-12-11\",\"days\":2,\"reason\":\"phase4b-reject\"}")
NO2=$(echo "$SUB2" | jq -r '.data.requestNo')
[ "$(echo "$SUB2" | jq -r '.data.approverChain | length')" = "1" ] \
  && ok "2 天 → 审批链 1 级" || bad "2 天的审批链级数不对"
for i in $(seq 1 30); do
  R=$(as "$M1" "$BASE/flow/todos" | jq -r '.data | length'); [ "$R" = "1" ] && break; sleep 2
done
T3=$(as "$M1" "$BASE/flow/todos" | jq -r '.data[0].taskId')
[ -n "$T3" ] && [ "$T3" != "null" ] && ok "驳回单的待办已出现" || { bad "驳回单没有待办"; T3=""; }
if [ -n "$T3" ]; then
  as "$M1" -X POST "$BASE/flow/todos/$T3/complete" -H 'Content-Type: application/json' \
    -d '{"outcome":"REJECT","comment":"不批"}' >/dev/null
  sleep 2
  [ "$(wf "$WF/api/v1/process-instances?definitionKey=oaGenericApproval&businessKey=${NO2}" | jq -r '.[0].running')" = "false" ] \
    && ok "REJECT 后流程立即终止（completionCondition 生效，没有继续上报）" || bad "驳回后流程仍在跑"
  [ "$(psq "SELECT status FROM oa_flow.leave_request WHERE request_no='${NO2}'")" = "REJECTED" ] \
    && ok "请假单 REJECTED" || bad "驳回单状态不对"
  BAL2=$(psq "SELECT used_days||'/'||frozen_days FROM oa_flow.leave_balance WHERE user_id='${U}'")
  [ "$BAL2" = "5.0/0.0" ] && ok "驳回释放冻结，已用天数未被误扣（仍是 5.0/0.0）" \
    || bad "驳回后额度应为 5.0/0.0，实为 ${BAL2}"
fi

echo; echo "════════════════════════════════════════"
echo "  Phase 4b（真中台集成）通过 ${PASS} / 失败 ${FAIL}"
echo "════════════════════════════════════════"
[ "$FAIL" -eq 0 ] || exit 1
