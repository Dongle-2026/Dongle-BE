# ============================================
# 유스레터 Spring Boot - 멀티스테이지 빌드
# Stage 1: Gradle 빌드 (큰 이미지, 빌드 후 버림)
# Stage 2: JRE만 포함 (경량 런타임)
#
# NOTE: jdk17 + eclipse-temurin:17-jre 조합
# (Apple Silicon arm64에서는 alpine 이미지가 지원되지 않아 Ubuntu JRE 사용)
# ============================================

# ── Stage 1: Build ──
FROM gradle:8.12-jdk17 AS build

WORKDIR /app

# 의존성 캐싱: build.gradle만 먼저 복사해서 의존성 레이어를 캐싱
# 소스 코드가 바뀌어도 의존성은 다시 안 받음
COPY build.gradle settings.gradle ./
RUN gradle dependencies --no-daemon || true

# 소스 복사 + 빌드
COPY src ./src
RUN gradle bootJar --no-daemon -x test

# ── Stage 2: Runtime ──
# eclipse-temurin:17-jre = Ubuntu 기반, arm64(Apple Silicon) + amd64 모두 지원
FROM eclipse-temurin:17-jre

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

# 보안: root가 아닌 전용 유저로 실행 (Ubuntu/Debian 문법)
RUN groupadd -r appgroup && useradd -r -g appgroup appuser
USER appuser

# 빌드 결과물만 복사
COPY --from=build /app/build/libs/*.jar app.jar

# 헬스체크
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
    CMD curl --fail --silent http://localhost:8080/api/health || exit 1

EXPOSE 8080

# JVM 메모리 옵션 (컨테이너 환경 최적화)
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
