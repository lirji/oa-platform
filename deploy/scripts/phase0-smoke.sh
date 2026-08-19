#!/usr/bin/env bash
# Phase 0 冒烟：基建 + 两道硬门禁。
# 本机 Testcontainers 跑不起来（见 local-dev-env），因此集成验证一律走"打运行中的 compose 容器"。
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DEPLOY="$ROOT/deploy"
PASS=0; FAIL=0

ok()   { echo "  ✅ $1"; PASS=$((PASS+1)); }
bad()  { echo "  ❌ $1"; FAIL=$((FAIL+1)); }
step() { echo; echo "── $1"; }

export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 21)}"
export PATH="/Users/liruijun/personal/devUtils/apache-maven-3.9.12/bin:$PATH"

set -a; [ -f "$DEPLOY/.env" ] && . "$DEPLOY/.env"; set +a
PG_PORT="${OA_PG_HOST_PORT:-35432}"; PG_DB="${OA_PG_DB:-oa}"; PG_USER="${OA_PG_USER:-oa}"
REDIS_PORT="${OA_REDIS_HOST_PORT:-36379}"; KAFKA_PORT="${OA_KAFKA_HOST_PORT:-39092}"
MINIO_PORT="${OA_MINIO_API_PORT:-39000}"

# ───────────────────────────────────────────── 1. 基建
step "1. 起基建（先 down --remove-orphans 清 docker-proxy 残留）"
docker compose -p oa-platform -f "$DEPLOY/docker-compose.yml" --env-file "$DEPLOY/.env" down --remove-orphans >/dev/null 2>&1
docker compose -p oa-platform -f "$DEPLOY/docker-compose.yml" --env-file "$DEPLOY/.env" up -d >/dev/null 2>&1 \
  && ok "compose up" || bad "compose up 失败"

echo "  等待容器 healthy ..."
for i in $(seq 1 60); do
  unhealthy=$(docker ps --filter "name=oa-" --format '{{.Names}} {{.Status}}' | grep -cv "healthy" || true)
  [ "$unhealthy" -eq 0 ] && break
  sleep 3
done
docker ps --filter "name=oa-" --format '     {{.Names}}  {{.Status}}'

# ───────────────────────────────────────────── 2. 中间件连通性
step "2. 中间件连通性"
docker exec oa-postgres pg_isready -U "$PG_USER" -d "$PG_DB" >/dev/null 2>&1 \
  && ok "PostgreSQL :$PG_PORT 就绪" || bad "PostgreSQL :$PG_PORT 不可达"

docker exec oa-postgres psql -U "$PG_USER" -d "$PG_DB" -tAc \
  "SELECT 1 FROM pg_available_extensions WHERE name='btree_gist'" 2>/dev/null | grep -q 1 \
  && ok "btree_gist 可用（会议室 EXCLUDE 排他约束依赖它）" || bad "btree_gist 不可用"

docker exec oa-postgres psql -U "$PG_USER" -d "$PG_DB" -tAc \
  "SELECT 1 FROM pg_available_extensions WHERE name='pgcrypto'" 2>/dev/null | grep -q 1 \
  && ok "pgcrypto 可用（敏感字段加密/哈希依赖它）" || bad "pgcrypto 不可用"

docker exec oa-redis redis-cli ping 2>/dev/null | grep -q PONG \
  && ok "Redis :$REDIS_PORT PONG" || bad "Redis :$REDIS_PORT 不可达"

docker exec oa-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 \
  --create --if-not-exists --topic oa.phase0.probe --partitions 1 --replication-factor 1 >/dev/null 2>&1 \
  && ok "Kafka :$KAFKA_PORT 建 topic 成功" || bad "Kafka :$KAFKA_PORT 建 topic 失败"

curl -sf "http://localhost:$MINIO_PORT/minio/health/live" >/dev/null 2>&1 \
  && ok "MinIO :$MINIO_PORT 存活" || bad "MinIO :$MINIO_PORT 不可达"

# ───────────────────────────────────────────── 3. 硬门禁 B
step "3. 硬门禁 B：中台制品可解析（system maven 仓库 /Users/liruijun/personal/repository）"
mvn -B -q -f "$ROOT/pom.xml" dependency:get \
  -Dartifact=com.lrj.workflow:workflow-platform-sdk:0.1.0-SNAPSHOT -o >/dev/null 2>&1 \
  && ok "com.lrj.workflow:workflow-platform-sdk 可解析" \
  || bad "workflow SDK 解析失败 —— 检查是否误用了 ./mvnw（走 ~/.m2）"

mvn -B -q -f "$ROOT/pom.xml" dependency:get \
  -Dartifact=com.lrj.authz:auth-platform-sdk:0.1.0-SNAPSHOT -o >/dev/null 2>&1 \
  && ok "com.lrj.authz:auth-platform-sdk 可解析" || bad "auth SDK 解析失败"

# ───────────────────────────────────────────── 4. 全量构建 + CI 纪律 + 硬门禁 A
step "4. 全量构建 + CI 纪律测试 + 硬门禁 A（虚拟线程 pinning 实测）"
BUILD_LOG=$(mktemp)
mvn -B -f "$ROOT/pom.xml" clean install -DskipTests >"$BUILD_LOG" 2>&1 \
  && ok "全 reactor 构建通过" || { bad "构建失败"; tail -30 "$BUILD_LOG"; }

TEST_LOG=$(mktemp)
mvn -B -f "$ROOT/pom.xml" -pl oa-app test >"$TEST_LOG" 2>&1
if grep -qE "Tests run:.*Failures: 0, Errors: 0" "$TEST_LOG" && ! grep -q "BUILD FAILURE" "$TEST_LOG"; then
  ok "oa-app 测试全绿（ArchUnit 纪律 + Controller 权限覆盖 + pinning 探针）"
else
  bad "oa-app 测试未全绿"; grep -E "Tests run|ERROR|FAIL" "$TEST_LOG" | head -20
fi
echo "  ── 硬门禁 A 实测输出 ──"
grep -E "\[对照组\]|\[硬门禁A\]" "$TEST_LOG" | sed 's/^/     /' || echo "     (未捕获到探针输出)"

# ───────────────────────────────────────────── 5. 应用启动 + Flyway
step "5. oa-app 启动 + Flyway 迁移 + /ping"
JAR=$(ls "$ROOT"/oa-app/target/oa-app-*.jar 2>/dev/null | grep -v original | head -1)
if [ -n "$JAR" ]; then
  java -jar "$JAR" >/tmp/oa-app-phase0.log 2>&1 &
  APP_PID=$!
  for i in $(seq 1 40); do
    curl -sf http://localhost:8400/api/v1/system/ping >/dev/null 2>&1 && break
    sleep 2
  done
  RESP=$(curl -sf http://localhost:8400/api/v1/system/ping 2>/dev/null)
  if echo "$RESP" | grep -q '"code":0'; then
    ok "oa-app :8400 /ping 正常 -> $RESP"
    echo "$RESP" | grep -q '"virtualThreads":true' \
      && ok "请求确实跑在虚拟线程上" || bad "请求未跑在虚拟线程上（检查 spring.threads.virtual.enabled）"
  else
    bad "oa-app :8400 /ping 失败"; tail -25 /tmp/oa-app-phase0.log
  fi
  docker exec oa-postgres psql -U "$PG_USER" -d "$PG_DB" -tAc \
    "SELECT count(*) FROM information_schema.schemata WHERE schema_name LIKE 'oa\_%'" 2>/dev/null | grep -q "7" \
    && ok "Flyway 建出 7 个业务 schema" || bad "Flyway schema 数量不符（期望 7）"
  kill $APP_PID 2>/dev/null; wait $APP_PID 2>/dev/null
else
  bad "找不到 oa-app jar"
fi

# ───────────────────────────────────────────── 汇总
echo; echo "════════════════════════════════════"
echo "  Phase 0 冒烟：通过 $PASS / 失败 $FAIL"
echo "════════════════════════════════════"
[ "$FAIL" -eq 0 ] || exit 1
