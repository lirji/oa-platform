#!/usr/bin/env bash
# oa-console 的演示与 e2e 数据夹具（Phase 3 step 0）。
#
# 为什么需要它：
#  ① 权限沙盘要展示"来自哪个部门继承 / 哪条临时授权"，而默认的 seed 数据里
#     所有授权都是 USER 直授 —— 中栏渲染出来全是"本人被直接授权"，
#     恰恰**看不出**继承这条最有说服力的链路；
#  ② e2e 要在"不同数据权限的两个人看同一个列表"上断言空态文案不同，
#     需要固定的、可预期的账号；
#  ③ 万人通讯录与 3000 节点树的验收，需要真的有那么多数据。
#
# 幂等：可重复执行。授权走 API（走 API 才会 bump epoch，裸 SQL 会绕过失效协议）。
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BASE="${OA_BASE:-http://localhost:8400/api/v1}"
ADMIN="${OA_ADMIN:-seed-user-1}"
ORGS="${OA_FIXTURE_ORGS:-3000}"
EMPLOYEES="${OA_FIXTURE_EMPLOYEES:-10000}"

ok(){ echo "  ✅ $1"; }
bad(){ echo "  ❌ $1"; }
step(){ echo; echo "── $1"; }

as(){ curl -s -H "X-OA-User: $1" "${@:2}"; }
psq(){ docker exec oa-postgres psql -U "${OA_PG_USER:-oa}" -d "${OA_PG_DB:-oa}" -tAc "$1" 2>/dev/null | tr -d ' \r'; }

curl -sf "$BASE/system/ping" >/dev/null || { bad "oa-app 不可达（$BASE）"; exit 1; }

step "1. 装载 ${ORGS} 组织 / ${EMPLOYEES} 员工"
as "$ADMIN" -X DELETE "$BASE/org/seed" >/dev/null 2>&1
SEED=$(as "$ADMIN" -X POST "$BASE/org/seed?orgs=${ORGS}&employees=${EMPLOYEES}")
GOT=$(echo "$SEED" | jq -r '.data.employees // 0')
[ "$GOT" = "$EMPLOYEES" ] && ok "装载完成（$(echo "$SEED" | jq -r '.data.elapsedMs') ms）" \
  || { bad "装载失败：$SEED"; exit 1; }

step "2. 五个固定账号"
# e2e 的身份矩阵。名字写死，spec 里直接引用；换环境只要重跑这个脚本。
R_SUPER=$(psq "SELECT id FROM oa_iam.role WHERE code='SUPER_ADMIN'")
R_HR=$(psq "SELECT id FROM oa_iam.role WHERE code='HR_ADMIN'")
R_MGR=$(psq "SELECT id FROM oa_iam.role WHERE code='DEPT_MANAGER'")
R_EMP=$(psq "SELECT id FROM oa_iam.role WHERE code='EMPLOYEE'")

# 挑一个有两级汇报线、且所在部门有兄弟部门的员工当"部门经理"样本 ——
# 有兄弟部门才看得出 ORG_AND_SUB 与 ALL 的差别。
E2E_MGR=$(psq "SELECT 'seed-user-'||e.id FROM oa_org.employee e
  JOIN oa_org.employee_org_assignment a ON a.employee_id=e.id AND a.valid_to IS NULL
  JOIN oa_org.org_unit o ON o.id=a.org_unit_id AND o.parent_id IS NOT NULL
  JOIN oa_org.reporting_line r ON r.employee_id=e.id AND r.valid_to IS NULL
  ORDER BY e.id LIMIT 1")
E2E_EMP=$(psq "SELECT 'seed-user-'||e.id FROM oa_org.employee e
  JOIN oa_org.employee_org_assignment a ON a.employee_id=e.id AND a.valid_to IS NULL AND a.is_leader=false
  ORDER BY e.id DESC LIMIT 1")
E2E_HR="seed-user-2"
E2E_NOPERM="e2e-no-perm"   # 刻意不给任何授权：验"无权限"与"未认证"的差别

grant(){  # $1=subjectType $2=subjectId $3=roleId $4=scopeType [$5=extra json]
  as "$ADMIN" -X POST "$BASE/iam/grants" -H 'Content-Type: application/json' \
    -d "{\"subjectType\":\"$1\",\"subjectId\":\"$2\",\"roleId\":$3,\"scopeType\":\"$4\"${5:-}}" \
    | jq -r '.code'
}
[ "$(grant USER "$ADMIN"   "$R_SUPER" ALL)"         = "0" ] && ok "super      = ${ADMIN}"      || bad "super 授权失败"
[ "$(grant USER "$E2E_HR"  "$R_HR"    ALL)"         = "0" ] && ok "hr         = ${E2E_HR}"     || bad "hr 授权失败"
[ "$(grant USER "$E2E_MGR" "$R_MGR"   ORG_AND_SUB)" = "0" ] && ok "manager    = ${E2E_MGR}"    || bad "manager 授权失败"
[ "$(grant USER "$E2E_EMP" "$R_EMP"   SELF)"        = "0" ] && ok "employee   = ${E2E_EMP}"    || bad "employee 授权失败"
ok "no-perm    = ${E2E_NOPERM}（刻意不授权）"

step "3. ★ 一条组织授权（含下级）—— 沙盘要靠它展示"部门继承""
# 找 E2E_EMP 的祖父级组织，授权给它并勾选 include_descendants。
# 这样沙盘查 E2E_EMP 时，来源链里才会出现"所在组织「X」被授权（含下级）"。
ANC=$(psq "SELECT o2.id FROM oa_org.employee e
  JOIN oa_org.employee_org_assignment a ON a.employee_id=e.id AND a.valid_to IS NULL
  JOIN oa_org.org_unit o1 ON o1.id=a.org_unit_id
  JOIN oa_org.org_unit o2 ON o1.path LIKE o2.path||'%' AND o2.id<>o1.id AND o2.depth=o1.depth-1
  WHERE e.user_id='${E2E_EMP}' LIMIT 1")
if [ -n "$ANC" ]; then
  ANC_NAME=$(psq "SELECT name FROM oa_org.org_unit WHERE id=${ANC}")
  [ "$(grant ORG_UNIT "$ANC" "$R_MGR" ORG_AND_SUB ',"includeDescendants":true,"reason":"沙盘演示：部门继承"')" = "0" ] \
    && ok "组织授权：「${ANC_NAME}」(id=${ANC}) 含下级 → DEPT_MANAGER" || bad "组织授权失败"
else
  bad "找不到 ${E2E_EMP} 的上级组织（组织层级可能只有一层）"
fi

step "4. ★ 一条临时授权 —— 沙盘要靠它展示"哪条临时授权""
VALID_TO=$(python3 -c "import datetime;print((datetime.datetime.now(datetime.timezone.utc)+datetime.timedelta(days=7)).isoformat())")
[ "$(grant USER "$E2E_MGR" "$R_HR" ALL ",\"grantType\":\"TEMPORARY\",\"validTo\":\"${VALID_TO}\",\"reason\":\"沙盘演示：临时顶岗\"")" = "0" ] \
  && ok "临时授权：${E2E_MGR} → HR_ADMIN，7 天后到期" || bad "临时授权失败"

step "5. 验证来源链确实能看出三种来源"
sleep 2
WHY=$(as "$ADMIN" "$BASE/iam/admin/why?userId=${E2E_EMP}&permCode=oa:employee:view")
VIAS=$(echo "$WHY" | jq -r '[.data.sources[].subjectType] | unique | join(",")')
echo "$VIAS" | grep -q "ORG_UNIT" \
  && ok "来源链含 ORG_UNIT（部门继承看得出来了）—— subjectType: ${VIAS}" \
  || bad "来源链只有 ${VIAS}，沙盘中栏展示不出继承"

step "6. 输出给 e2e 用的账号表"
# ★ 目录可能还不存在（oa-console 尚未创建），先建再写。
# 原来用 `cat > file || cat <<EOF` 兜底 —— 那个 heredoc 会被 shell 当成两段解析，
# 失败时把脚本剩余内容当文本打出来，看起来像"输出了"其实什么都没写。
FIXTURE_DIR="$ROOT/oa-console/e2e"
mkdir -p "$FIXTURE_DIR"
cat > "$FIXTURE_DIR/fixtures.json" <<JSON
{
  "super":    "${ADMIN}",
  "hr":       "${E2E_HR}",
  "manager":  "${E2E_MGR}",
  "employee": "${E2E_EMP}",
  "noPerm":   "${E2E_NOPERM}",
  "inheritedOrgId": ${ANC:-null},
  "orgs": ${ORGS},
  "employees": ${EMPLOYEES}
}
JSON
[ -s "$FIXTURE_DIR/fixtures.json" ] && ok "账号表 → oa-console/e2e/fixtures.json" || bad "账号表写入失败"

echo; echo "════════════════════════════════════════"
echo "  夹具就绪：${ORGS} 组织 / ${EMPLOYEES} 员工 / 5 个账号 / 组织授权 + 临时授权各一条"
echo "════════════════════════════════════════"
