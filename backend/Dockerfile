FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY backend/pom.xml ./pom.xml
RUN mvn -q dependency:go-offline
COPY backend/config ./config
COPY backend/src ./src
RUN mvn -q -Djava.awt.headless=true package
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S optifit && adduser -S optifit -G optifit
COPY --from=build /build/target/optifit-0.1.0.jar app.jar
USER optifit
EXPOSE 8080
ENTRYPOINT ["java", "-Djava.awt.headless=true", "-XX:MaxRAMPercentage=70", "-jar", "app.jar"]
