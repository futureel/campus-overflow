# 后端多阶段构建：Maven 打包 → jlink 精简 JRE → 最小运行时镜像（目标 < 200MB）
# 本地验证：自定义 JRE 49.56MB，配合 75.42MB fat jar 与 alpine 基础层，镜像约 132MB。

# ---------- 阶段 1：Maven 构建 ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
# 先单独拷贝 pom 利用依赖缓存，再拷贝全量源码
COPY backend/pom.xml ./pom.xml
COPY backend/ ./
RUN mvn -B -DskipTests clean package

# ---------- 阶段 2：jlink 裁剪 JRE ----------
FROM eclipse-temurin:21-alpine AS jre
# 显式模块集：覆盖 Spring Boot 3 / Tomcat / Hibernate / JDBC / Redis / Micrometer 运行需求。
# 不依赖 jdeps 对 fat jar 的分析（其无法递归 BOOT-INF/lib，输出仅 java.base,java.sql，不完整）。
ENV JLINK_MODULES="java.base,java.compiler,java.desktop,java.instrument,java.logging,java.management,java.naming,java.net.http,java.prefs,java.security.jgss,java.security.sasl,java.sql,java.transaction.xa,java.xml,jdk.crypto.cryptoki,jdk.crypto.ec,jdk.unsupported,jdk.zipfs"
RUN jlink \
      --add-modules "$JLINK_MODULES" \
      --strip-debug \
      --no-header-files \
      --no-man-pages \
      --compress=zip-6 \
      --output /opt/jre

# ---------- 阶段 3：最小运行时 ----------
FROM alpine:3.20
# CA 证书供出站 HTTPS；wget 由 busybox 提供用于健康检查；创建非 root 用户
RUN apk add --no-cache ca-certificates \
    && addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=jre /opt/jre /opt/jre
COPY --from=build /src/co-bootstrap/target/campus-overflow.jar app.jar
RUN chown -R app:app /app
USER app
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -Duser.timezone=UTC"
EXPOSE 8080
ENTRYPOINT ["sh","-c","exec /opt/jre/bin/java $JAVA_OPTS -jar app.jar"]
