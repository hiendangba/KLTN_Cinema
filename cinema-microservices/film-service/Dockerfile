FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app

COPY .. /app
RUN mvn -pl film-service -am clean package -DskipTests

FROM eclipse-temurin:17
WORKDIR /app
COPY --from=builder /app/film-service/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
