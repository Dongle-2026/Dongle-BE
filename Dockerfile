# ============================================
# 유스레터 Spring Boot - 멀티스테이지 빌드
# Stage 1: Gradle 빌드 (큰 이미지, 빌드 후 버림)
# Stage 2: JRE만 포함 (경량 런타임)
# ============================================

# ── Stage 1: Build ──
FROM gradle:8.12-jdk21 AS build

WORKDIR /app

# 의존성 캐싱: build.gradle만 먼저 복사해서 의존성 레이어를 캐싱
# 소스 코드가 바뀌어도 의존성은 다시 안 받음
COPY build.gradle settings.gradle ./
RUN gradle dependencies --no-daemon || true

# 소스 복사 + 빌드
COPY src ./src
RUN gradle bootJar --no-daemon -x test

# ── Stage 2: Runtime ──
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# 보안: root가 아닌 전용 유저로 실행
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# 빌드 결과물만 복사
COPY --from=build /app/build/libs/*.jar app.jar

# 헬스체크
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

EXPOSE 8080

# JVM 메모리 옵션 (컨테이너 환경 최적화)
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
