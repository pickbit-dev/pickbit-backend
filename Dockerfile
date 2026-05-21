FROM eclipse-temurin:25-jdk AS builder

WORKDIR /workspace

ARG SERVICE_NAME

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
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

RUN chmod +x ./gradlew \
    && ./gradlew --no-daemon :${SERVICE_NAME}:bootJar \
    && cp ${SERVICE_NAME}/build/libs/*.jar /app.jar

FROM eclipse-temurin:25-jre

WORKDIR /app

ENV JAVA_OPTS="" \
    TZ=Asia/Seoul

COPY --from=builder /app.jar /app/app.jar

EXPOSE 18080 18081 18082 18083 18084 18085 18086 18087

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
