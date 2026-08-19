#!/usr/bin/env bash
# Phase 2 权限域冒烟。
# 验收核心：改组织 / 改角色 / 临时授权 / 撤权 → 接口、数据范围、菜单立即随之变化。
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

# 以某个身份发请求（DEV 模式用 X-OA-User 切身份，方便对比不同人看到什么）
as()   { curl -s -H "X-OA-User: $1" "${@:2}"; }
code() { curl -s -o /dev/null -w '%{http_code}' -H "X-OA-User: $1" "${@:2}"; }
rc()   { as "$1" "${@:2}" | jq -r '.code // -1'; }
# ⚠️ reason 一律用 ASCII：非 ASCII 直接拼进 URL 查询串会让请求根本到不了服务端，
#    而且响应常被丢弃，排查起来极费劲（本脚本第一版就栽在这里）。
ungrant() { as "$ADMIN" -X DELETE "$BASE/iam/grants/$1?reason=smoke-cleanup" >/dev/null; sleep 1.5; }

# ───────────────────────────── 1. 启动
step "1. 构建并启动（开启装载 + 初始管理员引导）"
mvn -B -q -f "$ROOT/pom.xml" clean install -DskipTests >/tmp/oa-p2-build.log 2>&1 \
  && ok "构建通过" || { bad "构建失败"; tail -25 /tmp/oa-p2-build.log; exit 1; }

pkill -f 'oa-app-.*\.jar' 2>/dev/null; sleep 1
JAR=$(ls "$ROOT"/oa-app/target/oa-app-*.jar | grep -v original | head -1)
OA_ORG_SEED_ENABLED=true OA_BOOTSTRAP_ADMIN="$ADMIN" java -jar "$JAR" >/tmp/oa-app-phase2.log 2>&1 &
APP_PID=$!
cleanup() { kill $APP_PID 2>/dev/null; wait $APP_PID 2>/dev/null; }
trap cleanup EXIT
for i in $(seq 1 45); do curl -sf "$BASE/system/ping" >/dev/null 2>&1 && break; sleep 2; done
# ⚠️ /ping 通 ≠ 可用：初始管理员引导是 ApplicationRunner，跑在 Web 服务器启动【之后】。
# 必须等到管理员真的判得过权，否则前几个请求会撞上"还没授权"的空快照。
for i in $(seq 1 20); do
  [ "$(rc "$ADMIN" "$BASE/iam/admin/cache-stats")" = "0" ] && break; sleep 1
done
[ "$(rc "$ADMIN" "$BASE/iam/admin/cache-stats")" = "0" ] \
  && ok "oa-app :8400 就绪且初始管理员已可判权" || { bad "启动失败"; tail -40 /tmp/oa-app-phase2.log; exit 1; }

step "2. 装载组织与人员"
as "$ADMIN" -X DELETE "$BASE/org/seed" >/dev/null 2>&1
# 装载会 RESTART IDENTITY，seed-user-N 完全重现；不清授权的话，
# 上一轮的授权会被这一轮的同名用户直接继承，测出来全是残影。
psq "DELETE FROM oa_iam.delegation WHERE delegator_user_id LIKE 'seed-user-%' OR delegatee_user_id LIKE 'seed-user-%'" >/dev/null
# 保留初始管理员那条 —— BootstrapAdminInitializer 只在启动时补，删了这一轮就没人能授权了
psq "DELETE FROM oa_iam.grant_record WHERE subject_type='USER' AND subject_id LIKE 'seed-user-%' AND subject_id <> '${ADMIN}'" >/dev/null
psq "DELETE FROM oa_iam.grant_record WHERE subject_type='ORG_UNIT'" >/dev/null
psq "UPDATE oa_iam.perm_epoch SET epoch = epoch + 1" >/dev/null
SEED=$(as "$ADMIN" -X POST "$BASE/org/seed?orgs=3000&employees=10000")
[ "$(echo "$SEED" | jq -r '.data.employees // 0')" = "10000" ] && ok "10,000 员工就位" || { bad "装载失败: $SEED"; exit 1; }
sleep 2

U=$(psq "SELECT 'seed-user-'||e.id FROM oa_org.employee e JOIN oa_org.employee_org_assignment a ON a.employee_id=e.id AND a.valid_to IS NULL AND a.is_leader=false ORDER BY e.id LIMIT 1")
EID=$(psq "SELECT id FROM oa_org.employee WHERE user_id='${U}'")
U_ORG=$(psq "SELECT a.org_unit_id FROM oa_org.employee_org_assignment a WHERE a.employee_id=${EID} AND a.valid_to IS NULL LIMIT 1")
U_PATH=$(psq "SELECT path FROM oa_org.org_unit WHERE id=${U_ORG}")
R_EMPLOYEE=$(psq "SELECT id FROM oa_iam.role WHERE code='EMPLOYEE'")
R_MANAGER=$(psq "SELECT id FROM oa_iam.role WHERE code='DEPT_MANAGER'")
R_HR=$(psq "SELECT id FROM oa_iam.role WHERE code='HR_ADMIN'")
echo "     实验对象 ${U}（员工 ${EID}，组织 ${U_ORG} ${U_PATH}）"

# ───────────────────────────── 3. 接口权限
step "3. 接口权限：系统唯一的安全边界"
[ "$(rc "$U" "$BASE/org/units/tree")" = "3001" ] && ok "无授权用户被拒（3001）" || bad "无授权用户竟被放行"
[ "$(rc "$ADMIN" "$BASE/org/units/tree")" = "0" ] && ok "初始管理员可访问（走的是真实授权，不是判权后门）" || bad "初始管理员被拒"
[ "$(code "" "$BASE/org/units/tree")" = "401" ] && ok "无身份返回 401（区分'你是谁'与'你能不能'）" || bad "无身份未返回 401"

# ───────────────────────────── 4. 授权 / 撤权立即生效
step "4. 授权与撤权立即生效"
GID=$(as "$ADMIN" -X POST "$BASE/iam/grants" -H 'Content-Type: application/json' \
      -d "{\"subjectType\":\"USER\",\"subjectId\":\"${U}\",\"roleId\":${R_EMPLOYEE},\"scopeType\":\"SELF\"}" | jq -r '.data')
[ "$(rc "$U" "$BASE/org/units/tree")" = "0" ] && ok "授权后【立即】可访问（不重启、不等 TTL）" || bad "授权未立即生效"
[ "$(as "$U" "$BASE/me/permissions" | jq -r '.data.permCodes | length')" -ge 3 ] \
  && ok "/me/permissions 下发权限清单（前端据此裁剪菜单）" || bad "/me/permissions 未返回权限"
ungrant "$GID"
[ "$(rc "$U" "$BASE/org/units/tree")" = "3001" ] && ok "撤权后 1 秒内失效（收权走全局 epoch）" || bad "撤权未及时生效"

# ───────────────────────────── 5. 部门授权继承
step "5. 部门授权继承：两种语义必须可区分"
ANC=$(psq "SELECT ancestor_id FROM oa_org.org_closure WHERE descendant_id=${U_ORG} AND distance=2 LIMIT 1")
G2=$(as "$ADMIN" -X POST "$BASE/iam/grants" -H 'Content-Type: application/json' \
     -d "{\"subjectType\":\"ORG_UNIT\",\"subjectId\":\"${ANC}\",\"roleId\":${R_EMPLOYEE},\"scopeType\":\"ORG_AND_SUB\",\"includeDescendants\":true}" | jq -r '.data')
sleep 1.5
[ "$(rc "$U" "$BASE/org/units/tree")" = "0" ] && ok "授权给 2 层之上的部门，下级员工继承到了" || bad "include_descendants=true 未继承"
ungrant "$G2"

G3=$(as "$ADMIN" -X POST "$BASE/iam/grants" -H 'Content-Type: application/json' \
     -d "{\"subjectType\":\"ORG_UNIT\",\"subjectId\":\"${ANC}\",\"roleId\":${R_EMPLOYEE},\"scopeType\":\"ORG_AND_SUB\",\"includeDescendants\":false}" | jq -r '.data')
sleep 1.5
[ "$(rc "$U" "$BASE/org/units/tree")" = "3001" ] && ok "关掉 include_descendants 后下级不再继承" || bad "include_descendants=false 仍然继承了"
ungrant "$G3"

# ───────────────────────────── 6. 数据权限
step "6. 数据权限：同一个接口，不同的人看到不同的行"
TOTAL=$(psq "SELECT count(*) FROM oa_org.v_employee_directory")
SUBTREE=$(psq "SELECT count(*) FROM oa_org.v_employee_directory WHERE org_path LIKE '${U_PATH}%'")
echo "     全量 ${TOTAL} 人；${U} 所在子树 ${SUBTREE} 人"

GS=$(as "$ADMIN" -X POST "$BASE/iam/grants" -H 'Content-Type: application/json' \
     -d "{\"subjectType\":\"USER\",\"subjectId\":\"${U}\",\"roleId\":${R_EMPLOYEE},\"scopeType\":\"SELF\"}" | jq -r '.data')
V=$(as "$U" "$BASE/org/directory/count" | jq -r '.data.visible // -1')
[ "$V" = "1" ] && ok "SELF 范围：只看到自己 1 人" || bad "SELF 范围看到 ${V} 人，期望 1"
ungrant "$GS"

GO=$(as "$ADMIN" -X POST "$BASE/iam/grants" -H 'Content-Type: application/json' \
     -d "{\"subjectType\":\"USER\",\"subjectId\":\"${U}\",\"roleId\":${R_EMPLOYEE},\"scopeType\":\"ORG_AND_SUB\"}" | jq -r '.data')
V=$(as "$U" "$BASE/org/directory/count" | jq -r '.data.visible // -1')
[ "$V" = "$SUBTREE" ] && ok "ORG_AND_SUB 范围：看到本部门及子部门 ${V} 人" || bad "ORG_AND_SUB 看到 ${V} 人，期望 ${SUBTREE}"
[ "$(as "$ADMIN" "$BASE/org/directory/count" | jq -r '.data.visible')" = "$TOTAL" ] \
  && ok "ALL 范围：管理员看到全部 ${TOTAL} 人" || bad "ALL 范围人数不符"
PREFIX=$(as "$U" "$BASE/me/permissions" | jq -r '.data.scopePrefixes[0] // ""')
[ "$PREFIX" = "$U_PATH" ] && ok "下发的范围前缀 = ${PREFIX}（正是 SQL 里 LIKE 的那个）" || bad "范围前缀 ${PREFIX} ≠ ${U_PATH}"

# ───────────────────────────── 7. 列级脱敏
step "7. 列级脱敏：同一个 VO，权限决定明文还是掩码"
ungrant "$GO"
as "$ADMIN" -X PUT "$BASE/org/employees/${EID}" -H 'Content-Type: application/json' \
  -d '{"mobile":"13812345678"}' >/dev/null
# 用 SELF 范围，保证 directory 只返回实验对象本人 —— 否则取到的是别人的行，结果不确定
GS2=$(as "$ADMIN" -X POST "$BASE/iam/grants" -H 'Content-Type: application/json' \
      -d "{\"subjectType\":\"USER\",\"subjectId\":\"${U}\",\"roleId\":${R_EMPLOYEE},\"scopeType\":\"SELF\"}" | jq -r '.data')
M1=$(as "$U" "$BASE/org/directory?limit=1" | jq -r '.data[0].mobile // "null"')
[ "$M1" = "138****5678" ] && ok "无 oa:field:mobile → 掩码 ${M1}" || bad "未脱敏或格式异常: ${M1}"
ungrant "$GS2"
GM=$(as "$ADMIN" -X POST "$BASE/iam/grants" -H 'Content-Type: application/json' \
     -d "{\"subjectType\":\"USER\",\"subjectId\":\"${U}\",\"roleId\":${R_MANAGER},\"scopeType\":\"SELF\"}" | jq -r '.data')
M2=$(as "$U" "$BASE/org/directory?limit=1" | jq -r '.data[0].mobile // "null"')
[ "$M2" = "13812345678" ] && ok "有 oa:field:mobile → 明文 ${M2}（脱敏在序列化层，接口一行没改）" || bad "有权限仍被脱敏: ${M2}"
ungrant "$GM"

# ───────────────────────────── 8. 临时授权到点自动失效
step "8. 临时授权：到点自动失效，不依赖定时任务"
EXP=$(date -u -v+4S +%FT%TZ 2>/dev/null || date -u -d '+4 seconds' +%FT%TZ)
GT=$(as "$ADMIN" -X POST "$BASE/iam/grants" -H 'Content-Type: application/json' \
     -d "{\"subjectType\":\"USER\",\"subjectId\":\"${U}\",\"roleId\":${R_EMPLOYEE},\"scopeType\":\"SELF\",\"grantType\":\"TEMPORARY\",\"validTo\":\"${EXP}\"}" | jq -r '.data')
[ "$(rc "$U" "$BASE/org/units/tree")" = "0" ] && ok "临时授权期内可访问" || bad "临时授权期内被拒"
sleep 6
[ "$(rc "$U" "$BASE/org/units/tree")" = "3001" ] \
  && ok "过期后自动失效（快照 TTL 被压到到期时刻，没被 5 分钟 TTL 拖住）" || bad "过期临时授权仍放行"
RECLAIM=$(as "$ADMIN" -X POST "$BASE/iam/admin/reclaim-expired" | jq -r '.data.reclaimed // -1')
[ "$RECLAIM" -ge 1 ] 2>/dev/null && ok "到期回收归档了 ${RECLAIM} 条" || bad "到期回收没归档"

# ───────────────────────────── 9. JIT 提权
step "9. JIT 提权：高危操作即便有永久授权也要先激活"
TMP_ORG=$(as "$ADMIN" -X POST "$BASE/org/units" -H 'Content-Type: application/json' \
          -d "{\"parentId\":${U_ORG},\"code\":\"SMOKE-TMP-$$\",\"name\":\"smoke-temp\",\"type\":\"TEAM\"}" | jq -r '.data')
[ "$TMP_ORG" != "null" ] && ok "为提权测试新建了空组织 ${TMP_ORG}" || bad "新建测试组织失败"
sleep 1.5
GH=$(as "$ADMIN" -X POST "$BASE/iam/grants" -H 'Content-Type: application/json' \
     -d "{\"subjectType\":\"USER\",\"subjectId\":\"${U}\",\"roleId\":${R_HR},\"scopeType\":\"ALL\"}" | jq -r '.data')
[ "$(as "$U" -X DELETE "$BASE/org/units/${TMP_ORG}" | jq -r '.code')" = "3002" ] \
  && ok "持有 HR_ADMIN 永久授权，撤销组织仍被拒（3002 需要提权）" || bad "高危操作未要求提权"

FAKE=$(as "$U" -X POST "$BASE/iam/elevations" -H 'Content-Type: application/json' \
       -d "{\"roleId\":999999,\"reason\":\"try to escalate\",\"hours\":1}" | jq -r '.code')
[ "$FAKE" = "3001" ] && ok "提权到未持有的角色被拒（自助提权 ≠ 自助越权）" || bad "竟能提权到未持有的角色: ${FAKE}"

ELE=$(as "$U" -X POST "$BASE/iam/elevations" -H 'Content-Type: application/json' \
      -d "{\"roleId\":${R_HR},\"reason\":\"smoke: verify elevation path\",\"hours\":1}" | jq -r '.code')
[ "$ELE" = "0" ] && ok "提权申请受理（只允许激活已持有的角色）" || bad "提权申请失败: ${ELE}"
DIS=$(as "$U" -X DELETE "$BASE/org/units/${TMP_ORG}" | jq -r '.code')
[ "$DIS" = "0" ] && ok "激活已持有的角色后放行（操作已记入审计）" || bad "提权后仍被拒: ${DIS}"

# ───────────────────────────── 10. 委托代理
step "10. 委托代理：转移待办，不叠加权限"
B=$(psq "SELECT 'seed-user-'||id FROM oa_org.employee ORDER BY id DESC LIMIT 1")
GB=$(as "$ADMIN" -X POST "$BASE/iam/grants" -H 'Content-Type: application/json' \
     -d "{\"subjectType\":\"USER\",\"subjectId\":\"${B}\",\"roleId\":${R_EMPLOYEE},\"scopeType\":\"SELF\"}" | jq -r '.data')
END=$(date -u -v+1H +%FT%TZ 2>/dev/null || date -u -d '+1 hour' +%FT%TZ)
DR=$(as "$U" -X POST "$BASE/iam/delegations" -H 'Content-Type: application/json' \
     -d "{\"delegateeUserId\":\"${B}\",\"validTo\":\"${END}\",\"reason\":\"business trip\"}" | jq -r '.code')
[ "$DR" = "0" ] && ok "员工可自助委托（委托自己的待办不该是管理员权限）" || bad "委托被拒: ${DR}"
[ "$(as "$B" "$BASE/me/permissions" | jq -r '.data.delegators | index("'"${U}"'") // -1')" != "-1" ] \
  && ok "代理人快照里出现了被代理人 ${U}" || bad "委托关系未进入代理人快照"
[ "$(as "$B" "$BASE/org/directory/count" | jq -r '.data.visible')" = "1" ] \
  && ok "代理人数据范围没被放大（委托不叠加权限）" || bad "委托叠加了权限，这是越权"

# ───────────────────────────── 11. 改组织 → 数据范围立即跟着变
step "11. 改组织 → 数据范围立即跟着变"
# 把该用户的全部在效授权清空：除了 HR_ADMIN，第 9 步的 JIT 提权也带 ALL 范围（1 小时有效），
# 留着的话数据范围恒为 ALL，这一步就永远测不出组织变化的影响。
psq "UPDATE oa_iam.grant_record SET revoked_at=now(), revoked_by='smoke', revoke_reason='step11 reset' WHERE subject_type='USER' AND subject_id='${U}' AND revoked_at IS NULL" >/dev/null
psq "UPDATE oa_iam.perm_epoch SET epoch = epoch + 1" >/dev/null
sleep 1.5
GO2=$(as "$ADMIN" -X POST "$BASE/iam/grants" -H 'Content-Type: application/json' \
      -d "{\"subjectType\":\"USER\",\"subjectId\":\"${U}\",\"roleId\":${R_EMPLOYEE},\"scopeType\":\"ORG_AND_SUB\"}" | jq -r '.data')
BEFORE=$(as "$U" "$BASE/org/directory/count" | jq -r '.data.visible')
NEW_ORG=$(psq "SELECT id FROM oa_org.org_unit WHERE depth=3 AND id<>${U_ORG} ORDER BY id LIMIT 1")
as "$ADMIN" -X POST "$BASE/org/employees/${EID}/transfer" -H 'Content-Type: application/json' \
  -d "{\"targetOrgId\":${NEW_ORG},\"effectiveDate\":\"$(date +%F)\"}" >/dev/null
sleep 1.5
AFTER=$(as "$U" "$BASE/org/directory/count" | jq -r '.data.visible')
EXPECT=$(psq "SELECT count(*) FROM oa_org.v_employee_directory WHERE org_path LIKE (SELECT path FROM oa_org.org_unit WHERE id=${NEW_ORG})||'%'")
[ "$AFTER" = "$EXPECT" ] && [ "$AFTER" != "$BEFORE" ] \
  && ok "调岗后可见人数 ${BEFORE} → ${AFTER}（= 新部门子树 ${EXPECT}）" \
  || bad "调岗后可见人数 ${BEFORE} → ${AFTER}，期望 ${EXPECT}"

# ───────────────────────────── 12. 影子校验与热路径延迟
step "12. 影子校验一致性 + 判权热路径延迟"
EX=$(as "$ADMIN" "$BASE/iam/admin/explain?userId=${U}&permCode=oa:employee:view")
[ "$(echo "$EX" | jq -r '.data.consistent')" = "true" ] && ok "缓存与数据库重算一致（权限调试器可解释来源）" || bad "缓存与真值不一致: $EX"

BENCH=$(as "$ADMIN" "$BASE/iam/admin/bench?userId=${U}&iterations=50000")
P50=$(echo "$BENCH" | jq -r '.data.p50Us'); P99=$(echo "$BENCH" | jq -r '.data.p99Us')
echo "     判权延迟: p50=${P50}μs p99=${P99}μs"
awk -v p="$P99" 'BEGIN{exit !(p < 1000)}' && ok "判权 P99 = ${P99}μs < 1ms（热路径零 DB 零远程）" || bad "判权 P99 = ${P99}μs 超过 1ms"

sleep 2   # 等异步影子校验跑完再看指标
STATS=$(as "$ADMIN" "$BASE/iam/admin/cache-stats")
MIS=$(echo "$STATS" | jq -r '.data.shadowMismatches'); CHK=$(echo "$STATS" | jq -r '.data.shadowChecks')
[ "$MIS" = "0" ] && ok "影子校验 ${CHK} 次采样，不一致 0 次" || bad "影子校验出现 ${MIS} 次不一致"
echo "     缓存统计: $(echo "$STATS" | jq -c '.data')"

echo; echo "════════════════════════════════════"
echo "  Phase 2 权限域冒烟：通过 $PASS / 失败 $FAIL"
echo "════════════════════════════════════"
[ "$FAIL" -eq 0 ] || exit 1
