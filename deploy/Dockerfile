# syntax=docker/dockerfile:1
#
# OA Platform 运行镜像（四个可部署单元共用一份 Dockerfile，靠构建参数区分）。
#
# ⚠️ 刻意【不做】容器内 Maven 构建。原因很实在：本项目依赖的中台制品
# （com.lrj.workflow / com.lrj.authz）装在宿主机的 system maven 仓库
# /Users/liruijun/personal/repository，容器里看不到；要么把整个仓库挂进去，
# 要么把中台源码也搬进构建上下文——两条路都比"先在宿主机构建、镜像只装 jar"更脏。
#
# 构建入口统一走 deploy/build-images.sh，它会先用 system mvn 构建再 docker build。

ARG JRE_IMAGE=eclipse-temurin:21-jre
FROM ${JRE_IMAGE}

ARG SERVICE=oa-app
ARG JAR_FILE=oa-app/target/oa-app-0.1.0-SNAPSHOT.jar
ARG APP_PORT=8400

LABEL org.opencontainers.image.title="oa-platform/${SERVICE}" \
      org.opencontainers.image.description="企业级 OA 协同办公平台（万人级）" \
      org.opencontainers.image.source="https://github.com/liruijun/oa-platform"

# curl 用于容器健康检查；JRE 基础镜像里没有
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl tzdata \
 && rm -rf /var/lib/apt/lists/*

# ★ ARG 只在构建期存在，运行期就没了。健康检查是【运行期】执行的，
# 直接写 ${APP_PORT} 会取到空值并回落到默认 8400 ——
# 于是 notify/file/job 三个服务的健康检查全打到 8400，端点明明是 UP、容器却报 unhealthy。
# 必须显式转成 ENV 才能被运行期看见。
ENV APP_PORT=${APP_PORT}
ENV TZ=Asia/Shanghai
RUN useradd -r -u 1001 -m -s /usr/sbin/nologin oa
WORKDIR /app

COPY --chown=oa:oa ${JAR_FILE} /app/app.jar

USER oa
EXPOSE ${APP_PORT}

# MaxRAMPercentage 让 JVM 按【容器】的内存上限算堆，而不是按宿主机 ——
# 不设的话容器限 1G 而宿主机 32G 时，JVM 会以为自己能用 8G，然后被 OOMKill。
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"
ENV SPRING_PROFILES_ACTIVE=docker

# 用 exec 形式，让 java 成为 PID 1 直接收到 SIGTERM，配合 server.shutdown=graceful
# 才能把在途请求处理完再退出（打卡队列里的数据靠这个 flush 掉）。
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD curl -fsS "http://localhost:${APP_PORT}/actuator/health" || exit 1
