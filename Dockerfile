# syntax=docker/dockerfile:1

###############################################
# Stage 1 — Build: biên dịch & đóng gói jar
###############################################
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Cache dependency: copy pom trước, tải sẵn dependency rồi mới copy source.
# => đổi source code không phải tải lại toàn bộ dependency.
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q clean package -DskipTests

###############################################
# Stage 2 — Runtime: chỉ JRE + jar, gọn nhẹ
###############################################
FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app

# curl cho HEALTHCHECK (actuator), rồi dọn cache apt cho image nhỏ.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Chạy bằng user không phải root cho an toàn.
RUN groupadd --system spring && useradd --system --gid spring spring
USER spring:spring

# Lấy jar đã build từ stage 1.
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

# JAVA_OPTS để tùy chỉnh heap/GC khi chạy (mặc định rỗng).
ENV JAVA_OPTS=""

# Health theo Spring Boot Actuator.
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
