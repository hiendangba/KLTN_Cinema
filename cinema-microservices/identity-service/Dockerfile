FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app

# copy toàn bộ project (bao gồm parent POM)
COPY .. /app

# build đúng module identity-service
RUN mvn -pl identity-service -am clean package -DskipTests

FROM eclipse-temurin:17
WORKDIR /app
COPY --from=builder /app/identity-service/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
