#!/usr/bin/env bash
# 给 OA 控制台灌入一批可看见的演示数据（组织 / 员工 / 待办 / 请假 / 考勤 / 知识库 / 行政 / 通知）。
#
# 数据写进 PostgreSQL，不写进前端。幂等，可重复执行。
# 万人通讯录请继续用 seed-console-fixture.sh（需 DEV + OA_ORG_SEED_ENABLED）。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SQL="$ROOT/deploy/sql/seed-demo-data.sql"
PG_USER="${OA_PG_USER:-oa}"
PG_DB="${OA_PG_DB:-oa}"

ok(){ echo "  ✅ $1"; }
bad(){ echo "  ❌ $1"; exit 1; }
step(){ echo; echo "── $1"; }

docker container inspect oa-postgres >/dev/null 2>&1 || bad "oa-postgres 未运行"
[ -f "$SQL" ] || bad "找不到 $SQL"

step "1. 写入演示数据"
docker exec -i oa-postgres psql -v ON_ERROR_STOP=1 -U "$PG_USER" -d "$PG_DB" < "$SQL" \
  | sed -n '/NOTICE/p;/COMMIT/p;/ROLLBACK/p'
ok "SQL 已提交"

step "2. 查库核对"
psq(){ docker exec oa-postgres psql -U "$PG_USER" -d "$PG_DB" -tAc "$1" | tr -d ' \r'; }
[ "$(psq "SELECT count(*) FROM oa_org.org_unit WHERE code LIKE 'DEMO-%'")" = "11" ] && ok "11 个演示组织" || bad "组织数量不对"
[ "$(psq "SELECT count(*) FROM oa_org.employee WHERE emp_no LIKE 'DEMO-%'")" = "24" ] && ok "24 名演示员工" || bad "员工数量不对"
[ "$(psq "SELECT count(*) FROM oa_flow.todo_item WHERE task_id LIKE 'DEMO-%' AND state='PENDING'")" = "8" ] && ok "8 条待办" || bad "待办数量不对"
[ "$(psq "SELECT count(*) FROM oa_doc.kb_doc WHERE title LIKE '【演示】%'")" = "8" ] && ok "8 篇知识库文档" || bad "知识库数量不对"
[ "$(psq "SELECT count(*) FROM oa_att.punch_record WHERE device_id='DEMO-SEED'")" -ge "30" ] && ok "打卡记录已写入" || bad "打卡为空"

echo
echo "════════════════════════════════════════"
echo "  演示数据就绪。JWT 登录 Casdoor admin 后："
echo "    工作台待办 / 我发起的单据 / 组织树 / 通讯录 / 知识库 应能看到数据"
echo "  再跑：bash deploy/scripts/seed-demo-data.sh  （幂等）"
echo "════════════════════════════════════════"
