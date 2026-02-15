# Build Stage
FROM maven:3.9.6-eclipse-temurin-21 AS build
COPY src/main .
RUN mvn clean package -DskipTests

# Run Stage
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /target/teraunit-core-0.0.1-SNAPSHOT.jar app.jar

# Industrial Optimization for Virtual Threads
ENV JAVA_TOOL_OPTIONS="-XX:+UseZGC -XX:+ZGenerational"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
