# syntax=docker/dockerfile:1

# ── Stage 1: Build ───────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17-alpine AS builder
WORKDIR /build

# 先单独复制所有 pom.xml，利用 Docker 层缓存：只要 pom 不变，依赖下载层可复用
COPY pom.xml .
COPY hify-common/pom.xml    hify-common/
COPY hify-model/pom.xml     hify-model/
COPY hify-agent/pom.xml     hify-agent/
COPY hify-conversation/pom.xml hify-conversation/
COPY hify-knowledge/pom.xml hify-knowledge/
COPY hify-workflow/pom.xml  hify-workflow/
COPY hify-mcp/pom.xml       hify-mcp/
COPY hify-app/pom.xml       hify-app/
RUN mvn dependency:go-offline -B -q

# 复制源码并打包（-pl hify-app -am 只构建 app 及其依赖模块）
COPY . .
RUN mvn clean package -DskipTests -pl hify-app -am -B -q && \
    cp hify-app/target/hify-app-*.jar /build/app.jar

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 非 root 用户运行，降低容器权限
RUN addgroup -S hify \
    && adduser -S hify -G hify \
    && mkdir -p /app/logs /app/upload \
    && chown -R hify:hify /app

COPY --from=builder --chown=hify:hify /build/app.jar /app/app.jar

EXPOSE 8080

USER hify

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -q --spider http://127.0.0.1:8080/api/v1/health || exit 1

# JAVA_OPTS 可在不同环境中覆盖，例如：-Xms512m -Xmx1g -Dspring.profiles.active=prod
ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom -jar /app/app.jar"]
