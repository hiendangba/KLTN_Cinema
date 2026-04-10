# syntax=docker/dockerfile:1.7
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app

COPY . /app
RUN --mount=type=cache,target=/root/.m2 mvn -pl email-service -am -DskipTests package

FROM eclipse-temurin:17
WORKDIR /app
COPY --from=builder /app/email-service/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
