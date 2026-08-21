#!/usr/bin/env bash
set -euo pipefail

APP_BASE="${OA_PHASE9_APP_BASE:-http://127.0.0.1:18400}"
ADMIN="${OA_PHASE9_ADMIN:-seed-user-1}"
MEMBER="${OA_PHASE9_MEMBER:-seed-user-10000}"
ABAC_USER="${OA_PHASE9_ABAC_USER:-seed-user-544}"
GROUP_CODE="QA_IAM_POLICY_SMOKE"

hdr=(-H "X-OA-User: ${ADMIN}" -H 'Content-Type: application/json')
group_id=""
grant_id=""
condition_id=""

cleanup() {
  if [[ -n "$condition_id" ]]; then
    curl -fsS "${hdr[@]}" -X DELETE "${APP_BASE}/api/v1/iam/abac/conditions/${condition_id}" >/dev/null || true
  fi
  if [[ -n "$grant_id" ]]; then
    curl -fsS "${hdr[@]}" -X DELETE "${APP_BASE}/api/v1/iam/grants/${grant_id}?reason=phase9-cleanup" >/dev/null || true
  fi
  if [[ -n "$group_id" ]]; then
    curl -fsS "${hdr[@]}" -X DELETE "${APP_BASE}/api/v1/iam/groups/${group_id}/members/${MEMBER}" >/dev/null || true
    curl -fsS "${hdr[@]}" -X POST "${APP_BASE}/api/v1/iam/groups/${group_id}/enabled?enabled=false" >/dev/null || true
  fi
}
trap cleanup EXIT

assert_json() {
  local label="$1" body="$2" filter="$3"
  if ! printf '%s' "$body" | jq -e "$filter" >/dev/null; then
    printf 'FAIL %-28s %s\n' "$label" "$body" >&2
    exit 1
  fi
  printf 'PASS %s\n' "$label"
}

health=$(curl -fsS "${APP_BASE}/actuator/health")
assert_json 'service health' "$health" '.status == "UP"'

roles=$(curl -fsS "${hdr[@]}" "${APP_BASE}/api/v1/iam/roles")
hr_role=$(printf '%s' "$roles" | jq -er '.data[] | select(.code == "HR_ADMIN") | .id')
employee_role=$(printf '%s' "$roles" | jq -er '.data[] | select(.code == "EMPLOYEE") | .id')
catalog=$(curl -fsS "${hdr[@]}" "${APP_BASE}/api/v1/iam/permissions/catalog")
employee_view=$(printf '%s' "$catalog" | jq -er '.data[] | select(.code == "oa:employee:view") | .id')

groups=$(curl -fsS "${hdr[@]}" "${APP_BASE}/api/v1/iam/groups")
group_id=$(printf '%s' "$groups" | jq -r --arg code "$GROUP_CODE" '.data[] | select(.code == $code) | .id' | head -1)
if [[ -z "$group_id" ]]; then
  created=$(curl -fsS "${hdr[@]}" -d "{\"code\":\"${GROUP_CODE}\",\"name\":\"Phase 9 IAM policy smoke\",\"description\":\"可重复冒烟记录，脚本结束后禁用\"}" "${APP_BASE}/api/v1/iam/groups")
  group_id=$(printf '%s' "$created" | jq -er '.data')
else
  curl -fsS "${hdr[@]}" -X POST "${APP_BASE}/api/v1/iam/groups/${group_id}/enabled?enabled=true" >/dev/null
fi
[[ "$group_id" =~ ^[0-9]+$ ]] || { printf 'invalid group id: %s\n' "$group_id" >&2; exit 1; }
printf 'PASS group create/reuse id=%s\n' "$group_id"

member=$(curl -fsS "${hdr[@]}" -d "{\"userIds\":[\"${MEMBER}\"]}" "${APP_BASE}/api/v1/iam/groups/${group_id}/members")
assert_json 'group member add' "$member" '.data.affected == 1'

grant=$(curl -fsS "${hdr[@]}" -d "{\"subjectType\":\"USER_GROUP\",\"subjectId\":\"${group_id}\",\"roleId\":${hr_role},\"scopeType\":\"SELF\",\"reason\":\"phase9 USER_GROUP smoke\"}" "${APP_BASE}/api/v1/iam/grants")
grant_id=$(printf '%s' "$grant" | jq -er '.data')
[[ "$grant_id" =~ ^[0-9]+$ ]] || { printf 'invalid grant id: %s\n' "$grant_id" >&2; exit 1; }
mine=$(curl -fsS -H "X-OA-User: ${MEMBER}" "${APP_BASE}/api/v1/me/permissions")
assert_json 'group permission effective' "$mine" '.data.permCodes | index("oa:employee:create") != null'

why=$(curl -fsS "${hdr[@]}" "${APP_BASE}/api/v1/iam/admin/why?userId=${MEMBER}&permCode=oa:employee:create")
assert_json 'USER_GROUP source explain' "$why" '.data.sources | any(.subjectType == "USER_GROUP")'

validated=$(curl -fsS "${hdr[@]}" -d '{"expression":"#p0.amount <= 5000 and #user.tenantId == 1"}' "${APP_BASE}/api/v1/iam/abac/validate")
assert_json 'ABAC expression validate' "$validated" '.data.valid == true'

curl -fsS "${hdr[@]}" -X DELETE "${APP_BASE}/api/v1/iam/grants/${grant_id}?reason=phase9-revoke" >/dev/null
grant_id=""
after=$(curl -fsS -H "X-OA-User: ${MEMBER}" "${APP_BASE}/api/v1/me/permissions")
assert_json 'group permission revoked' "$after" '.data.permCodes | index("oa:employee:create") == null'

condition=$(curl -fsS "${hdr[@]}" -d "{\"roleId\":${employee_role},\"permissionId\":${employee_view},\"expression\":\"false\",\"description\":\"phase9 ABAC runtime smoke\",\"enabled\":true}" "${APP_BASE}/api/v1/iam/abac/conditions")
condition_id=$(printf '%s' "$condition" | jq -er '.data')
denied_status=$(curl -sS -o /dev/null -w '%{http_code}' -H "X-OA-User: ${ABAC_USER}" "${APP_BASE}/api/v1/org/employees/by-user/${ABAC_USER}")
[[ "$denied_status" = "403" ]] || { printf 'FAIL ABAC runtime deny HTTP %s\n' "$denied_status" >&2; exit 1; }
printf 'PASS ABAC runtime deny\n'
curl -fsS "${hdr[@]}" -X POST "${APP_BASE}/api/v1/iam/abac/conditions/${condition_id}/enabled?enabled=false" >/dev/null
allowed_status=$(curl -sS -o /dev/null -w '%{http_code}' -H "X-OA-User: ${ABAC_USER}" "${APP_BASE}/api/v1/org/employees/by-user/${ABAC_USER}")
[[ "$allowed_status" = "200" ]] || { printf 'FAIL ABAC disabled restore HTTP %s\n' "$allowed_status" >&2; exit 1; }
printf 'PASS ABAC disabled restores RBAC\n'
listed=$(curl -fsS "${hdr[@]}" "${APP_BASE}/api/v1/iam/abac/conditions?roleId=${employee_role}&permissionId=${employee_view}")
assert_json 'ABAC CRUD/toggle' "$listed" ".data | any(.id == ${condition_id} and .enabled == false)"

printf 'Phase 9 USER_GROUP/ABAC smoke passed. Cleanup will remove member, delete condition and disable group.\n'
