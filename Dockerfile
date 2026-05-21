FROM eclipse-temurin:25-jdk AS builder

WORKDIR /workspace

ARG SERVICE_NAME

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY library ./library
COPY ${SERVICE_NAME} ./${SERVICE_NAME}

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
