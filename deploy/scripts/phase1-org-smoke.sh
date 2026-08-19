#!/usr/bin/env bash
# Phase 1 组织域冒烟。前置：phase0-smoke.sh 起过基建（PG 35432 等容器在跑）。
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DEPLOY="$ROOT/deploy"
BASE="http://localhost:8400/api/v1"
PASS=0; FAIL=0
ok()   { echo "  ✅ $1"; PASS=$((PASS+1)); }
bad()  { echo "  ❌ $1"; FAIL=$((FAIL+1)); }
step() { echo; echo "── $1"; }

export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 21)}"
export PATH="/Users/liruijun/personal/devUtils/apache-maven-3.9.12/bin:$PATH"
set -a; [ -f "$DEPLOY/.env" ] && . "$DEPLOY/.env"; set +a
PG_USER="${OA_PG_USER:-oa}"; PG_DB="${OA_PG_DB:-oa}"
psq() { docker exec oa-postgres psql -U "$PG_USER" -d "$PG_DB" -tAc "$1" 2>/dev/null | tr -d ' \r'; }

# ───────────────────────────── 1. 启动
step "1. 构建并启动 oa-app（开启装载开关）"
mvn -B -q -f "$ROOT/pom.xml" clean install -DskipTests >/tmp/oa-p1-build.log 2>&1 \
  && ok "构建通过" || { bad "构建失败"; tail -25 /tmp/oa-p1-build.log; exit 1; }

pkill -f 'oa-app-.*\.jar' 2>/dev/null; sleep 1
JAR=$(ls "$ROOT"/oa-app/target/oa-app-*.jar | grep -v original | head -1)
OA_ORG_SEED_ENABLED=true java -jar "$JAR" >/tmp/oa-app-phase1.log 2>&1 &
APP_PID=$!
for i in $(seq 1 45); do curl -sf "$BASE/system/ping" >/dev/null 2>&1 && break; sleep 2; done
curl -sf "$BASE/system/ping" >/dev/null 2>&1 && ok "oa-app :8400 就绪" || { bad "启动失败"; tail -30 /tmp/oa-app-phase1.log; exit 1; }

cleanup() { kill $APP_PID 2>/dev/null; wait $APP_PID 2>/dev/null; }
trap cleanup EXIT

# ───────────────────────────── 2. 万人级装载
step "2. 批量装载 3,000 组织 + 10,000 员工（验收：< 30s）"
curl -sf -X DELETE "$BASE/org/seed" >/dev/null 2>&1
SEED=$(curl -sf -X POST "$BASE/org/seed?orgs=3000&employees=10000")
ELAPSED=$(echo "$SEED" | jq -r '.data.elapsedMs // 999999')
ORGS=$(echo "$SEED" | jq -r '.data.orgs // 0')
EMPS=$(echo "$SEED" | jq -r '.data.employees // 0')
CLOSURE=$(echo "$SEED" | jq -r '.data.closureRows // 0')
DEPTH=$(echo "$SEED" | jq -r '.data.maxDepth // 0')
RL=$(echo "$SEED" | jq -r '.data.reportingLines // 0')
echo "     组织=$ORGS 闭包=$CLOSURE 员工=$EMPS 汇报线=$RL 最大深度=$DEPTH 耗时=${ELAPSED}ms"
[ "$ORGS" = "3000" ] && [ "$EMPS" = "10000" ] && ok "装载数量正确" || bad "装载数量不符"
[ "$ELAPSED" -lt 30000 ] 2>/dev/null && ok "装载耗时 ${ELAPSED}ms < 30s" || bad "装载超过 30s（${ELAPSED}ms）"
[ "$DEPTH" -ge 5 ] 2>/dev/null && ok "组织树深度 $DEPTH 层（贴近万人企业形态）" || bad "组织树太浅（$DEPTH 层）"

# ───────────────────────────── 3. 闭包 / 路径一致性
step "3. 闭包表与物化路径一致性"
BAD=$(curl -sf "$BASE/org/units/consistency" | jq -r '.data.inconsistencies // -1')
[ "$BAD" = "0" ] && ok "closure 与 path 完全一致（0 条不一致）" || bad "存在 $BAD 条不一致"

ROOT_ID=$(psq "SELECT id FROM oa_org.org_unit WHERE parent_id IS NULL")
DESC_ALL=$(curl -sf "$BASE/org/units/$ROOT_ID/descendants" | jq -r '.data | length')
[ "$DESC_ALL" = "3000" ] && ok "根的后代数 = 3000（含自身，内存快照与库一致）" || bad "根的后代数 = ${DESC_ALL}，期望 3000"

# ───────────────────────────── 4. 移动子树
step "4. 移动子树：path / depth / closure 三者必须同步改变"
# 取一个 depth=2 且有子树的节点 X，和一个不在 X 子树内的 depth=1 节点 Y
X=$(psq "SELECT id FROM oa_org.org_unit WHERE depth=2 ORDER BY id LIMIT 1")
Y=$(psq "SELECT id FROM oa_org.org_unit WHERE depth=1 AND id NOT IN (SELECT descendant_id FROM oa_org.org_closure WHERE ancestor_id=$X) ORDER BY id DESC LIMIT 1")
X_SUB_BEFORE=$(psq "SELECT count(*) FROM oa_org.org_closure WHERE ancestor_id=$X")
X_PATH_BEFORE=$(psq "SELECT path FROM oa_org.org_unit WHERE id=$X")
Y_PATH=$(psq "SELECT path FROM oa_org.org_unit WHERE id=$Y")
echo "     把 $X ($X_PATH_BEFORE, 子树 $X_SUB_BEFORE 节点) 移到 $Y ($Y_PATH) 之下"

MOVE=$(curl -sf -o /dev/null -w '%{http_code}' -X PUT "$BASE/org/units/$X/parent" \
        -H 'Content-Type: application/json' -d "{\"newParentId\":$Y}")
[ "$MOVE" = "200" ] && ok "移动接口返回 200" || bad "移动接口返回 $MOVE"

X_PATH_AFTER=$(psq "SELECT path FROM oa_org.org_unit WHERE id=$X")
case "$X_PATH_AFTER" in "$Y_PATH$X/") ok "X 的新 path = ${X_PATH_AFTER}（挂在 Y 之下）";; *) bad "X 的 path 错误: ${X_PATH_AFTER}，期望 $Y_PATH$X/";; esac

ORPHAN=$(psq "SELECT count(*) FROM oa_org.org_unit WHERE path LIKE '$X_PATH_BEFORE%'")
[ "$ORPHAN" = "0" ] && ok "旧路径前缀下已无残留节点（整棵子树都平移了）" || bad "还有 $ORPHAN 个节点留在旧路径前缀下"

SUB_AFTER=$(psq "SELECT count(*) FROM oa_org.org_closure WHERE ancestor_id=$X")
[ "$SUB_AFTER" = "$X_SUB_BEFORE" ] && ok "子树内部结构未变（仍 $SUB_AFTER 个节点）" || bad "子树节点数从 $X_SUB_BEFORE 变成 $SUB_AFTER"

Y_HAS_X=$(psq "SELECT count(*) FROM oa_org.org_closure WHERE ancestor_id=$Y AND descendant_id=$X")
[ "$Y_HAS_X" = "1" ] && ok "closure 已建立 Y→X 的祖先边" || bad "closure 缺少 Y→X 的边"

DEPTH_OK=$(psq "SELECT count(*) FROM oa_org.org_closure c JOIN oa_org.org_unit a ON a.id=c.ancestor_id JOIN oa_org.org_unit d ON d.id=c.descendant_id WHERE c.distance <> d.depth - a.depth")
[ "$DEPTH_OK" = "0" ] && ok "所有 distance 与 depth 差值一致（深度平移正确）" || bad "$DEPTH_OK 条 distance 与 depth 对不上"

BAD2=$(curl -sf "$BASE/org/units/consistency" | jq -r '.data.inconsistencies // -1')
[ "$BAD2" = "0" ] && ok "移动后一致性仍为 0" || bad "移动后出现 $BAD2 条不一致"

# 内存快照是否同步（同进程内事件驱动，应当立即生效）
MEM_PATH=$(curl -sf "$BASE/org/units/$X" | jq -r '.data.path')
[ "$MEM_PATH" = "$X_PATH_AFTER" ] && ok "内存快照已同步（无需等轮询）" || bad "内存快照仍是旧值: $MEM_PATH"

# ───────────────────────────── 5. 成环防护
step "5. 成环防护"
CHILD_OF_Y=$(psq "SELECT descendant_id FROM oa_org.org_closure WHERE ancestor_id=$Y AND distance=1 LIMIT 1")
CODE=$(curl -s -o /tmp/oa-cycle.json -w '%{http_code}' -X PUT "$BASE/org/units/$Y/parent" \
        -H 'Content-Type: application/json' -d "{\"newParentId\":$CHILD_OF_Y}")
MSG=$(jq -r '.code // 0' /tmp/oa-cycle.json)
[ "$MSG" = "2002" ] && ok "把父节点挂到自己子节点下被拒（ORG_CYCLE 2002，HTTP ${CODE}）" || bad "成环未被拦下: HTTP $CODE code=$MSG"

CODE=$(curl -s -o /tmp/oa-self.json -w '%{http_code}' -X PUT "$BASE/org/units/$Y/parent" \
        -H 'Content-Type: application/json' -d "{\"newParentId\":$Y}")
[ "$(jq -r '.code' /tmp/oa-self.json)" = "2002" ] && ok "挂到自己下面被拒" || bad "自挂未被拦下"

# ───────────────────────────── 6. 一人多岗与主岗唯一
step "6. 一人多岗：兼岗可加，主岗唯一由数据库守"
EMP=$(psq "SELECT id FROM oa_org.employee ORDER BY id LIMIT 1")
OTHER_ORG=$(psq "SELECT id FROM oa_org.org_unit WHERE depth>=3 ORDER BY id DESC LIMIT 1")
ADD=$(curl -s -X POST "$BASE/org/employees/$EMP/assignments" -H 'Content-Type: application/json' \
      -d "{\"orgUnitId\":$OTHER_ORG,\"assignmentType\":\"CONCURRENT\"}")
[ "$(echo "$ADD" | jq -r '.code')" = "0" ] && ok "兼岗添加成功" || bad "兼岗添加失败: $ADD"

CNT=$(curl -sf "$BASE/org/employees/by-user/seed-user-$EMP/assignments" | jq -r '.data | length')
[ "$CNT" = "2" ] && ok "该员工现有 2 段有效任职（主岗 + 兼岗）" || bad "任职数 = ${CNT}，期望 2"

DUP=$(curl -s -X POST "$BASE/org/employees/$EMP/assignments" -H 'Content-Type: application/json' \
      -d "{\"orgUnitId\":$OTHER_ORG,\"assignmentType\":\"PRIMARY\"}")
[ "$(echo "$DUP" | jq -r '.code')" = "2011" ] && ok "经此接口加主岗被拒（2011，必须走调岗）" || bad "主岗未被拦: $DUP"

# ───────────────────────────── 7. 调岗与历史还原
step "7. 调岗后 as-of 历史还原"
USER="seed-user-$EMP"
ORG_BEFORE=$(curl -sf "$BASE/org/employees/by-user/$USER" | jq -r '.data.primaryOrgId')
TODAY=$(date +%F); YESTERDAY=$(date -v-1d +%F 2>/dev/null || date -d 'yesterday' +%F)
TARGET=$(psq "SELECT id FROM oa_org.org_unit WHERE depth>=3 AND id<>$ORG_BEFORE ORDER BY id LIMIT 1")
TR=$(curl -s -X POST "$BASE/org/employees/$EMP/transfer" -H 'Content-Type: application/json' \
     -d "{\"targetOrgId\":$TARGET,\"effectiveDate\":\"$TODAY\",\"reason\":\"冒烟\"}")
[ "$(echo "$TR" | jq -r '.code')" = "0" ] && ok "调岗成功 $ORG_BEFORE -> $TARGET" || bad "调岗失败: $TR"

NOW_ORG=$(curl -sf "$BASE/org/employees/by-user/$USER/as-of?date=$TODAY" | jq -r '.data.primaryOrgId')
OLD_ORG=$(curl -sf "$BASE/org/employees/by-user/$USER/as-of?date=$YESTERDAY" | jq -r '.data.primaryOrgId')
[ "$NOW_ORG" = "$TARGET" ]     && ok "as-of 今天 = 新组织 $TARGET" || bad "as-of 今天 = ${NOW_ORG}，期望 $TARGET"
[ "$OLD_ORG" = "$ORG_BEFORE" ] && ok "as-of 昨天 = 旧组织 ${ORG_BEFORE}（历史可还原）" || bad "as-of 昨天 = ${OLD_ORG}，期望 $ORG_BEFORE"

# ───────────────────────────── 8. 汇报线与最小前缀
step "8. 汇报链与最小路径前缀"
# 必须挑「最深组织里的非负责人」：负责人本人可能已在链条顶端而没有上级，
# 而且要避开前面步骤调岗过的样本，否则测的是脏数据不是汇报链。
DEEP_EMP=$(psq "SELECT e.id FROM oa_org.employee e JOIN oa_org.employee_org_assignment a ON a.employee_id=e.id AND a.valid_to IS NULL AND a.assignment_type='PRIMARY' AND a.is_leader=false JOIN oa_org.org_unit o ON o.id=a.org_unit_id ORDER BY o.depth DESC, e.id LIMIT 1")
CHAIN=$(curl -sf "$BASE/org/employees/by-user/seed-user-$DEEP_EMP/manager-chain?maxLevel=10" | jq -r '.data | length')
[ "$CHAIN" -ge 2 ] 2>/dev/null && ok "逐级上报链 $CHAIN 级（汇报线独立于组织树，确实走通）" || bad "上报链只有 $CHAIN 级"

# 父 + 子 一起传，期望被归约成只剩父的路径
PARENT=$(psq "SELECT id FROM oa_org.org_unit WHERE depth=1 ORDER BY id LIMIT 1")
CHILD=$(psq "SELECT descendant_id FROM oa_org.org_closure WHERE ancestor_id=$PARENT AND distance=2 LIMIT 1")
PFX=$(curl -sf -X POST "$BASE/org/units/path-prefixes" -H 'Content-Type: application/json' -d "[$PARENT,$CHILD]" | jq -r '.data | length')
[ "$PFX" = "1" ] && ok "最小前缀归约生效：{父,孙} 归约成 1 个前缀" || bad "前缀数 = ${PFX}，期望 1"

# ───────────────────────────── 9. 前缀索引真的走了吗
step "9. org_path 前缀匹配必须走索引（ADR-0007 / 风险 R3）"
# ⚠️ 在 3,000 行的小表上 PG 选 Seq Scan 是【正确】的计划选择，不能据此判定索引没建对。
# 真正要证明的是「该索引对前缀谓词可用」：关掉 seqscan 逼它用索引，再看 Index Cond。
# text_pattern_ops 的标志是谓词被改写成 ~>=~ / ~<~ 范围扫描；opclass 配错就改写不出来。
DEEP_PATH=$(psq "SELECT path FROM oa_org.org_unit WHERE depth=3 ORDER BY id LIMIT 1")
PLAN=$(docker exec oa-postgres psql -U "$PG_USER" -d "$PG_DB" -tAc \
  "SET enable_seqscan=off; EXPLAIN (COSTS OFF) SELECT id FROM oa_org.org_unit WHERE path LIKE '$DEEP_PATH%'" 2>/dev/null)
echo "$PLAN" | grep -q "ix_org_path" \
  && ok "前缀谓词可命中 ix_org_path" || bad "索引对前缀谓词不可用 —— $PLAN"
echo "$PLAN" | grep -q "~>=~" \
  && ok "谓词被改写成范围扫描 ~>=~ / ~<~，证明 text_pattern_ops 生效" \
  || bad "未见 ~>=~ 范围改写，opclass 可能不是 text_pattern_ops —— $PLAN"

# ───────────────────────────── 10. 撤销防护
step "10. 撤销防护"
NONEMPTY=$(psq "SELECT o.id FROM oa_org.org_unit o WHERE EXISTS (SELECT 1 FROM oa_org.org_unit c WHERE c.parent_id=o.id) LIMIT 1")
D=$(curl -s -X DELETE "$BASE/org/units/$NONEMPTY")
[ "$(echo "$D" | jq -r '.code')" = "2003" ] && ok "有子组织时撤销被拒（2003）" || bad "撤销防护失效: $D"

echo; echo "════════════════════════════════════"
echo "  Phase 1 组织域冒烟：通过 $PASS / 失败 $FAIL"
echo "════════════════════════════════════"
[ "$FAIL" -eq 0 ] || exit 1
