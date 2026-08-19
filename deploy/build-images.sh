#!/usr/bin/env bash
# 构建四个可部署单元的镜像。
#
# 必须先用 system mvn 构建：中台制品在 /Users/liruijun/personal/repository，
# 容器里看不到（详见 Dockerfile 顶部说明）。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TAG="${OA_IMAGE_TAG:-local}"

export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 21)}"
export PATH="/Users/liruijun/personal/devUtils/apache-maven-3.9.12/bin:$PATH"

echo "── 1/2 构建 jar（system mvn，跳过测试；要跑测试请单独执行 mvn test）"
mvn -B -q -f "$ROOT/pom.xml" clean package -DskipTests

echo "── 2/2 构建镜像 tag=${TAG}"
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

echo
docker images --filter "reference=oa-platform/*:${TAG}" --format '   {{.Repository}}:{{.Tag}}  {{.Size}}'
echo
echo "起全栈： docker compose -p oa-platform -f deploy/docker-compose.yml --profile apps up -d"
