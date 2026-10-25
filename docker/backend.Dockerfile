# ---------- 后端构建阶段 ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY backend/pom.xml .
RUN mvn -B -q dependency:go-offline
COPY backend/src ./src
RUN mvn -B -q package -DskipTests

# ---------- 运行阶段 ----------
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN mkdir -p /app/data/storage /app/logs
COPY --from=build /build/target/devpilot-backend-*.jar app.jar
ENV SERVER_PORT=8080 \
    STORAGE_PATH=/app/data/storage \
    JAVA_OPTS=""
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD curl -fs http://127.0.0.1:8080/actuator/health || exit 1
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
