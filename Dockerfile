FROM eclipse-temurin:25-jdk AS builder

WORKDIR /workspace

ARG SERVICE_NAME

# 1단계: 빌드 스크립트만 먼저 복사해 의존성 해석 결과를 별도 레이어로 캐싱한다.
# 소스를 먼저 복사하면 한 줄만 고쳐도 8개 이미지가 전체 의존성 그래프를 다시 내려받는다.
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY library/build.gradle ./library/
COPY logging-library/build.gradle ./logging-library/
COPY auth-service/build.gradle ./auth-service/
COPY user-service/build.gradle ./user-service/
COPY product-service/build.gradle ./product-service/
COPY auction-service/build.gradle ./auction-service/
COPY payment-service/build.gradle ./payment-service/
COPY notification-service/build.gradle ./notification-service/
COPY file-service/build.gradle ./file-service/
COPY gateway-service/build.gradle ./gateway-service/

# 의존성만 미리 받아둔다. 여기서 실패해도 아래 bootJar 단계에서 다시 시도하므로 빌드를 막지 않는다.
RUN chmod +x ./gradlew \
    && ./gradlew --no-daemon :${SERVICE_NAME}:dependencies > /dev/null 2>&1 || true

# 2단계: 소스 복사 후 실제 빌드. 여기부터가 커밋마다 바뀌는 레이어다.
COPY library ./library
COPY logging-library ./logging-library
COPY auth-service ./auth-service
COPY user-service ./user-service
COPY product-service ./product-service
COPY auction-service ./auction-service
COPY payment-service ./payment-service
COPY notification-service ./notification-service
COPY file-service ./file-service
COPY gateway-service ./gateway-service

RUN ./gradlew --no-daemon :${SERVICE_NAME}:bootJar \
    && cp ${SERVICE_NAME}/build/libs/*.jar /app.jar

FROM eclipse-temurin:25-jre

WORKDIR /app

# 컴포즈 헬스체크가 /actuator/health 를 호출한다.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

ENV JAVA_OPTS="" \
    TZ=Asia/Seoul

COPY --from=builder /app.jar /app/app.jar

EXPOSE 18080 18081 18082 18083 18084 18085 18086 18087

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
