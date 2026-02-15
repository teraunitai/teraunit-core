# Build Stage
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /build

# 1. Copy pom.xml first (Cache Layer)
COPY pom.xml .

# 2. Copy Source
COPY src ./src

# 3. Build (Memory Safe)
RUN mvn clean package -DskipTests

# Run Stage
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# 4. Copy the JAR (WILDCARD FIX)
# We use *.jar to automatically grab whatever version Maven produced
COPY --from=build /build/target/teraunit-core-*.jar app.jar

# 5. Industrial Optimization
ENV JAVA_TOOL_OPTIONS="-XX:+UseZGC -XX:+ZGenerational"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]