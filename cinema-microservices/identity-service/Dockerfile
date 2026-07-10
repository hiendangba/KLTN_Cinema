# syntax=docker/dockerfile:1.7
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app

COPY . /app
RUN --mount=type=cache,target=/root/.m2 mvn -DskipTests -pl identity-service -am package

FROM eclipse-temurin:17
WORKDIR /app
ENV JAVA_OPTS="-Xms96m -Xmx320m -XX:+UseSerialGC -XX:MaxMetaspaceSize=256m -XX:ReservedCodeCacheSize=96m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp -XX:+ExitOnOutOfMemoryError"
COPY --from=builder /app/identity-service/target/*.jar app.jar
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

