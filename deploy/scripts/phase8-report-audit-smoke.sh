#!/usr/bin/env bash
# Phase 8 冒烟：报表驾驶舱 / 审计 / 跑批 / 文件服务 / ★ 渗透用例（越权·IDOR·前端绕过）。
#
# 验收线（FINAL_PLAN §13）：
#   · 低权限用户直调高权限接口 403
#   · 改 URL id 越权取数返回 403/空
#   · 前端隐藏的按钮对应接口后端依然拦截
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DEPLOY="$ROOT/deploy"
BASE="http://localhost:8400/api/v1"
F="http://localhost:8402/api/v1"
J="http://localhost:8403/api/v1"
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

step "1. 构建并启动 app(8400) + file(8402) + job(8403)"
mvn -B -q -f "$ROOT/pom.xml" clean install -DskipTests >/tmp/oa-p8-build.log 2>&1 \
  && ok "构建通过（clean）" || { bad "构建失败"; tail -25 /tmp/oa-p8-build.log; exit 1; }
docker stop oa-app oa-file oa-job >/dev/null 2>&1
pkill -f 'oa-app-.*\.jar' 2>/dev/null; pkill -f 'oa-file-service-.*\.jar' 2>/dev/null
pkill -f 'oa-job-service-.*\.jar' 2>/dev/null; sleep 2
docker exec oa-redis redis-cli FLUSHDB >/dev/null 2>&1   # 清 L2 必须在启动【之前】
OA_ORG_SEED_ENABLED=true OA_BOOTSTRAP_ADMIN="$ADMIN" OA_WORKFLOW_MODE=LOCAL \
  java -jar "$(ls "$ROOT"/oa-app/target/oa-app-*.jar | grep -v original | head -1)" >/tmp/oa-p8-app.log 2>&1 &
APP_PID=$!
java -jar "$(ls "$ROOT"/oa-file-service/target/oa-file-service-*.jar | grep -v original | head -1)" >/tmp/oa-p8-file.log 2>&1 &
FILE_PID=$!
java -jar "$(ls "$ROOT"/oa-job-service/target/oa-job-service-*.jar | grep -v original | head -1)" >/tmp/oa-p8-job.log 2>&1 &
JOB_PID=$!
cleanup(){ kill $APP_PID $FILE_PID $JOB_PID 2>/dev/null; wait $APP_PID $FILE_PID $JOB_PID 2>/dev/null; }
trap cleanup EXIT
for i in $(seq 1 45); do curl -sf "$BASE/system/ping" >/dev/null 2>&1 && break; sleep 2; done
for i in $(seq 1 45); do curl -sf "$F/file/ping" >/dev/null 2>&1 && break; sleep 2; done
for i in $(seq 1 45); do curl -sf "$J/job/ping"  >/dev/null 2>&1 && break; sleep 2; done
curl -sf "$BASE/system/ping" >/dev/null && ok "oa-app 就绪" || { bad "app 启动失败"; tail -30 /tmp/oa-p8-app.log; exit 1; }
curl -sf "$F/file/ping" >/dev/null && ok "oa-file 就绪" || bad "file 启动失败"
curl -sf "$J/job/ping"  >/dev/null && ok "oa-job 就绪"  || bad "job 启动失败"

step "2. 迁移：审计分区 + 跑批 + 文件"
[ "$(psq "SELECT count(*) FROM pg_tables WHERE schemaname='oa_sys' AND tablename LIKE 'audit_log%'")" -ge 13 ] \
  && ok "审计表按月分区（12 月 + 兜底）" || bad "审计分区数不对"
[ -n "$(psq "SELECT 1 FROM pg_tables WHERE schemaname='oa_sys' AND tablename='job_run'")" ] \
  && ok "跑批记录表就位" || bad "job_run 缺失"
[ -n "$(psq "SELECT 1 FROM pg_tables WHERE schemaname='oa_sys' AND tablename='file_object'")" ] \
  && ok "文件元数据表就位" || bad "file_object 缺失"
[ "$(psq "SELECT count(*) FROM pg_views WHERE schemaname='oa_sys' AND viewname LIKE 'v_%'")" = "4" ] \
  && ok "4 个驾驶舱视图就位" || bad "驾驶舱视图数不对"
[ "$(psq "SELECT count(*) FROM information_schema.tables WHERE table_schema='oa_sys'
          AND table_name IN ('flyway_schema_history_job','flyway_schema_history_file')")" = "0" ] \
  && ok "独立服务的迁移史各自独立（不与 oa-app 共用）" || bad "迁移史位置异常"

step "3. 装载与授权"
as "$ADMIN" -X DELETE "$BASE/org/seed" >/dev/null 2>&1
psq "TRUNCATE oa_sys.audit_log; DELETE FROM oa_sys.job_run; DELETE FROM oa_sys.file_object" >/dev/null
SEED=$(as "$ADMIN" -X POST "$BASE/org/seed?orgs=100&employees=500")
[ "$(echo "$SEED" | jq -r '.data.employees // 0')" = "500" ] && ok "500 员工就位" || { bad "装载失败"; exit 1; }
R_SUPER=$(psq "SELECT id FROM oa_iam.role WHERE code='SUPER_ADMIN'")
R_EMP=$(psq "SELECT id FROM oa_iam.role WHERE code='EMPLOYEE'")
as "$ADMIN" -X POST "$BASE/iam/grants" -H 'Content-Type: application/json' \
  -d "{\"subjectType\":\"USER\",\"subjectId\":\"${ADMIN}\",\"roleId\":${R_SUPER},\"scopeType\":\"ALL\"}" >/dev/null
for u in seed-user-60 seed-user-61; do
  as "$ADMIN" -X POST "$BASE/iam/grants" -H 'Content-Type: application/json' \
    -d "{\"subjectType\":\"USER\",\"subjectId\":\"${u}\",\"roleId\":${R_EMP},\"scopeType\":\"SELF\"}" >/dev/null
done
sleep 1; ok "管理员 SUPER_ADMIN，两名普通员工 EMPLOYEE"

step "4. 管理驾驶舱"
[ "$(as "$ADMIN" "$BASE/report/headcount?limit=3" | jq -r '.data | length')" = "3" ] \
  && ok "编制人效可查" || bad "编制人效查询失败"
[ "$(as "$ADMIN" "$BASE/report/overview" | jq -r '.data | keys | length')" = "4" ] \
  && ok "一屏概览返回 4 组数据（避免前端发 6 个请求）" || bad "概览结构不对"
[ "$(http seed-user-60 "$BASE/report/overview")" = "403" ] \
  && ok "普通员工看不到驾驶舱" || bad "驾驶舱对普通员工开放了"

step "5. ★ 审计：写操作自动记录，且【被拒绝的也记】"
psq "TRUNCATE oa_sys.audit_log" >/dev/null
http seed-user-60 -X POST "$BASE/doc/official" -H 'Content-Type: application/json' -d '{"direction":"OUT","title":"越权尝试"}' >/dev/null
http seed-user-60 -X POST "$BASE/iam/grants" -H 'Content-Type: application/json' -d '{"subjectType":"USER","subjectId":"x","roleId":1,"scopeType":"ALL"}' >/dev/null
as "$ADMIN" -X POST "$BASE/doc/official" -H 'Content-Type: application/json' -d '{"direction":"OUT","title":"正常公文"}' >/dev/null
as "$ADMIN" -X POST "$BASE/report/audit/flush" >/dev/null
[ "$(psq "SELECT count(*) FROM oa_sys.audit_log WHERE outcome='DENIED'")" = "2" ] \
  && ok "2 条 DENIED 被记下（审计切面必须在判权切面【外层】，否则一条都记不到）" \
  || bad "DENIED 记录数不对：$(psq "SELECT count(*) FROM oa_sys.audit_log WHERE outcome='DENIED'")"
[ "$(psq "SELECT count(*) FROM oa_sys.audit_log WHERE outcome='SUCCESS'")" -ge "1" ] \
  && ok "成功的写操作也被记下" || bad "SUCCESS 没记"
[ "$(psq "SELECT count(*) FROM oa_sys.audit_log WHERE action='oa:doc:draft' AND actor_id='seed-user-60'")" = "1" ] \
  && ok "动作名用权限点 code（谁试探了哪个能力一目了然）" || bad "动作名不对"
[ "$(as "$ADMIN" "$BASE/report/audit/stats" | jq -r '.data.dropped')" = "0" ] \
  && ok "审计队列零丢弃（异步有界队列，丢了必须可观测）" || bad "审计有丢弃"

step "6. 审计查询需要 JIT 临时提权"
# ★ 先撤掉上一轮遗留的临时授权：JIT 提权默认活 1 小时，跨两次冒烟仍然有效，
#   会让"未提权应被拒"这条断言假失败。清完必须重启才生效（L1 在进程内）——
#   这里改走 API 撤销，它会正确 bump epoch，不需要重启。
for gid in $(psq "SELECT id FROM oa_iam.grant_record WHERE grant_type <> 'PERMANENT' AND revoked_at IS NULL"); do
  as "$ADMIN" -X DELETE "$BASE/iam/grants/${gid}?reason=smoke-reset" >/dev/null 2>&1
done
sleep 2
[ "$(as "$ADMIN" "$BASE/report/audit?limit=3" | jq -r '.code')" = "3002" ] \
  && ok "持 SUPER_ADMIN 仍被拒（3002）—— 审计是权限最高的只读数据" || bad "审计未要求提权"
as "$ADMIN" -X POST "$BASE/iam/elevations" -H 'Content-Type: application/json' \
  -d "{\"roleId\":${R_SUPER},\"reason\":\"排查审计\",\"hours\":1}" >/dev/null
sleep 1
[ "$(as "$ADMIN" "$BASE/report/audit?limit=3" | jq -r '.code')" = "0" ] \
  && ok "提权后可查（"我要查审计"成为有记录、有时限的动作）" || bad "提权后仍查不了"
[ "$(as "$ADMIN" "$BASE/report/audit?deniedOnly=true" | jq -r '.data | length')" = "2" ] \
  && ok "能只看被拒记录（安全排查最常问的那一问）" || bad "deniedOnly 过滤不对"

step "7. 跑批：幂等 + 分片"
psq "DELETE FROM oa_sys.job_run" >/dev/null
as "$ADMIN" -X POST "$J/job/grant-expire" >/dev/null
[ "$(psq "SELECT status FROM oa_sys.job_run WHERE job_name='grant-expire'")" = "SUCCESS" ] \
  && ok "授权到期回收执行成功" || bad "grant-expire 失败：$(psq "SELECT error FROM oa_sys.job_run WHERE job_name='grant-expire'")"
as "$ADMIN" -X POST "$J/job/grant-expire" >/dev/null
[ "$(psq "SELECT count(*) FROM oa_sys.job_run WHERE job_name='grant-expire'")" = "1" ] \
  && ok "重复触发被幂等挡下（唯一约束抢坑，不用分布式锁）" || bad "幂等失效"
as "$ADMIN" -X POST "$J/job/partition-roll" >/dev/null
[ "$(psq "SELECT status FROM oa_sys.job_run WHERE job_name='partition-roll'")" = "SUCCESS" ] \
  && ok "分区滚动执行成功（打卡不能因为运维忘建分区就丢）" || bad "partition-roll 失败"
as "$ADMIN" -X POST "$J/job/attendance-daily?day=$(date -v-1d +%Y-%m-%d 2>/dev/null || date -d yesterday +%Y-%m-%d)" >/dev/null
SHARDS=$(psq "SELECT count(*) FROM oa_sys.job_run WHERE job_name='attendance-daily'")
[ "$SHARDS" = "16" ] && ok "考勤日结分 16 片执行（employee_id % 16，不按 id 区间切）" \
  || bad "分片数应为 16，实为 ${SHARDS}"
[ "$(http seed-user-60 -X POST "$J/job/grant-expire")" = "403" ] \
  && ok "普通员工不能触发跑批" || bad "跑批接口对普通员工开放了"

step "8. 文件服务：上传 / 下载 / 预签名"
echo "phase8 smoke $(date)" > /tmp/oa-p8-upload.txt
UP=$(curl -s -H "X-OA-User: seed-user-60" -F "file=@/tmp/oa-p8-upload.txt" -F "bizType=KB" "$F/file")
FID=$(echo "$UP" | jq -r '.data.id')
[ -n "$FID" ] && [ "$FID" != "null" ] && ok "上传成功（id=${FID}）" || { bad "上传失败：$UP"; FID=0; }
KEY=$(echo "$UP" | jq -r '.data.objectKey')
echo "$KEY" | grep -qE '[0-9a-f]{8}-[0-9a-f]{4}' \
  && ok "object_key 不可枚举（UUID；可枚举的 key + 预签名 = 拿到一个就能推别人的）" \
  || bad "object_key 可枚举：${KEY}"
curl -s -o /tmp/oa-p8-dl.txt -H "X-OA-User: seed-user-60" "$F/file/${FID}/download"
diff -q /tmp/oa-p8-upload.txt /tmp/oa-p8-dl.txt >/dev/null \
  && ok "下载内容与上传一致" || bad "下载内容不一致"
PRE=$(as seed-user-60 "$F/file/${FID}/presign" | jq -r '.data.url')
curl -s -o /tmp/oa-p8-pre.txt "$PRE"
diff -q /tmp/oa-p8-upload.txt /tmp/oa-p8-pre.txt >/dev/null \
  && ok "预签名 URL 可直取（判权通过后才签发，短时效）" || bad "预签名取不到内容"

step "9. ★ 渗透用例（验收标准第 5 条）"
echo "     ① 低权限用户直调高权限接口"
[ "$(http seed-user-60 -X POST "$BASE/iam/grants" -H 'Content-Type: application/json' \
     -d '{"subjectType":"USER","subjectId":"self","roleId":1,"scopeType":"ALL"}')" = "403" ] \
  && ok "员工不能给自己授权（自助提权 ≠ 自助越权）" || bad "员工能给自己授权"
[ "$(http seed-user-60 "$BASE/report/audit")" = "403" ] && ok "员工看不到审计" || bad "员工能看审计"
[ "$(http seed-user-60 -X POST "$BASE/doc/official/1/issue")" = "403" ] \
  && ok "员工不能核发公文" || bad "员工能核发公文"

echo "     ② IDOR：改 URL id 取别人的数据"
[ "$(http seed-user-61 "$F/file/${FID}/download")" = "403" ] \
  && ok "改文件 id 下载别人的文件 → 403" || bad "文件 IDOR 未堵住"
[ "$(http seed-user-61 "$F/file/${FID}/presign")" = "403" ] \
  && ok "改文件 id 预签名 → 403（否则等于绕过全部应用层判权）" || bad "预签名 IDOR 未堵住"
KBID=$(as "$ADMIN" -X POST "$BASE/doc/kb" -H 'Content-Type: application/json' \
        -d '{"folderId":1,"title":"管理层文档","body":"机密"}' | jq -r '.data.id')
[ "$(http seed-user-60 "$BASE/doc/kb/${KBID}")" = "403" ] \
  && ok "改知识库 id 读别人的文档 → 403（接口权限之外还有对象级）" || bad "知识库 IDOR 未堵住"
MSG=$(psq "SELECT id FROM oa_flow.leave_request ORDER BY id DESC LIMIT 1")

echo "     ③ 前端隐藏的按钮，后端依然拦"
# 前端会根据 /iam/me 的权限列表隐藏按钮；这里跳过前端直接打接口，后端必须拦住。
MYPERMS=$(as seed-user-60 "$BASE/me/permissions" | jq -r '.data.permCodes // [] | length')
[ "$MYPERMS" -gt 0 ] && ok "前端能拿到自己的权限清单（用于隐藏按钮，仅体验）" || bad "拿不到权限清单"
[ "$(http seed-user-60 -X POST "$BASE/org/units" -H 'Content-Type: application/json' \
     -d '{"parentId":1,"code":"HACK","name":"偷建部门","type":"DEPT"}')" = "403" ] \
  && ok "绕过前端直调建组织接口 → 403（页面权限只是体验，接口权限才是边界）" \
  || bad "绕过前端能建组织"

echo "     ④ 未认证访问"
[ "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/doc/official" \
     -H 'Content-Type: application/json' -d '{"direction":"OUT","title":"x"}')" != "200" ] \
  && ok "无身份的写请求被拒" || bad "无身份也能写"

step "10. 审计闭环：渗透动作全部留痕"
as "$ADMIN" -X POST "$BASE/report/audit/flush" >/dev/null
DENIED=$(psq "SELECT count(*) FROM oa_sys.audit_log WHERE outcome='DENIED' AND actor_id='seed-user-60'")
[ "$DENIED" -ge 4 ] \
  && ok "seed-user-60 的 ${DENIED} 次越权尝试全部留痕（能回答"谁在试探什么"）" \
  || bad "越权尝试留痕不足：${DENIED}"

echo; echo "════════════════════════════════════════"
echo "  Phase 8 报表/审计/跑批/文件/渗透：通过 ${PASS} / 失败 ${FAIL}"
echo "════════════════════════════════════════"
[ "$FAIL" -eq 0 ] || exit 1
