# syntax=docker/dockerfile:1.7
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app

COPY . /app
RUN --mount=type=cache,target=/root/.m2 mvn -DskipTests -pl booking-service -am package

FROM eclipse-temurin:17
WORKDIR /app
ENV JAVA_OPTS="-Xms64m -Xmx224m -XX:MaxMetaspaceSize=160m -XX:ReservedCodeCacheSize=64m"
COPY --from=builder /app/booking-service/target/*.jar app.jar
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
