#!/usr/bin/env bash
# 构建四个后端可部署单元 + PC/移动端镜像。
#
# 必须先用 system mvn 构建：中台制品在 /Users/liruijun/personal/repository，
# 容器里看不到（详见 Dockerfile 顶部说明）。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

set -a
[ -f "$ROOT/deploy/.env" ] && . "$ROOT/deploy/.env"
set +a

TAG="${OA_IMAGE_TAG:-local}"

export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 21)}"
export PATH="/Users/liruijun/personal/devUtils/apache-maven-3.9.12/bin:$PATH"

echo "── 1/4 构建 jar（system mvn，跳过测试；要跑测试请单独执行 mvn test）"
mvn -B -q -f "$ROOT/pom.xml" clean package -DskipTests

echo "── 2/4 构建后端镜像 tag=${TAG}"
build() {  # $1=service  $2=port
  local svc="$1" port="$2"
  local jar
  jar=$(ls "$ROOT/$svc/target/$svc"-*.jar | grep -v original | head -1)
  jar="${jar#"$ROOT/"}"
  echo "   · $svc  <- $jar"
  docker build -q \
    -f "$ROOT/deploy/Dockerfile" \
    --build-arg "SERVICE=$svc" \
    --build-arg "JAR_FILE=$jar" \
    --build-arg "APP_PORT=$port" \
    -t "oa-platform/$svc:$TAG" \
    "$ROOT" >/dev/null
}

build oa-app            8400
build oa-notify-service 8401
build oa-file-service   8402
build oa-job-service    8403

if [ "${OA_BUILD_CONSOLE:-true}" = "true" ]; then
  echo "── 3/4 构建 PC 控制台镜像"
  docker build -q \
    -f "$ROOT/oa-console/Dockerfile" \
    --build-arg "FRONTEND_OIDC_ENABLED=${VITE_AUTH_ENABLED:-false}" \
    --build-arg "VITE_CASDOOR_AUTHORITY=${VITE_CASDOOR_AUTHORITY:-http://localhost:8000}" \
    --build-arg "VITE_CASDOOR_CLIENT_ID=${VITE_CASDOOR_CLIENT_ID:-}" \
    --build-arg "VITE_OIDC_SCOPE=${VITE_OIDC_SCOPE:-openid profile email offline_access}" \
    --build-arg "VITE_WS_PATH=${VITE_WS_PATH:-/ws}" \
    -t "oa-platform/oa-console:$TAG" \
    "$ROOT/oa-console" >/dev/null
else
  echo "── 3/4 跳过 PC 控制台镜像（OA_BUILD_CONSOLE=false）"
fi

if [ "${OA_BUILD_MOBILE:-true}" = "true" ]; then
  echo "── 4/4 构建移动端镜像"
  docker build -q \
    -f "$ROOT/oa-mobile/Dockerfile" \
    --build-arg "FRONTEND_OIDC_ENABLED=${VITE_AUTH_ENABLED:-false}" \
    --build-arg "VITE_CASDOOR_AUTHORITY=${VITE_CASDOOR_AUTHORITY:-http://localhost:8000}" \
    --build-arg "VITE_CASDOOR_CLIENT_ID=${VITE_CASDOOR_CLIENT_ID:-}" \
    --build-arg "VITE_OIDC_SCOPE=${VITE_OIDC_SCOPE:-openid profile email offline_access}" \
    -t "oa-platform/oa-mobile:$TAG" \
    "$ROOT/oa-mobile" >/dev/null
else
  echo "── 4/4 跳过移动端镜像（OA_BUILD_MOBILE=false）"
fi

echo
docker images --filter "reference=oa-platform/*:${TAG}" --format '   {{.Repository}}:{{.Tag}}  {{.Size}}'
echo
echo "起全栈： docker compose -p oa-platform -f deploy/docker-compose.yml --profile apps up -d"
