# 1. fáze: Sestavení aplikace pomocí Mavenu a JDK 21
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
# Stažení závislostí dopředu (cache)
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

# 2. fáze: Samotný lehký běh (Runtime)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/svatebni-kviz-1.0.0.jar app.jar

EXPOSE 7070
CMD ["java", "-jar", "app.jar"]