FROM maven:3.9.11-eclipse-temurin-17 AS builder
WORKDIR /app

COPY pom.xml .
COPY common-lib ./common-lib
COPY seat-service ./seat-service

RUN --mount=type=cache,target=/root/.m2 mvn -DskipTests -pl seat-service -am package

FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=builder /app/seat-service/target/*.jar app.jar

EXPOSE 8099 9199

ENTRYPOINT ["java","-jar","app.jar"]
