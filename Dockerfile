# Build Stage
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /build

# 1. Copy the pom.xml first to leverage Docker layer caching
COPY pom.xml .

# 2. Copy the entire source directory
COPY src ./src

# 3. Build the industrial JAR
RUN mvn clean package -DskipTests

# Run Stage
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# 4. Copy the JAR from the build stage
COPY --from=build /build/target/teraunit-core-0.0.1-SNAPSHOT.jar app.jar

# 5. Industrial Optimization for Virtual Threads
ENV JAVA_TOOL_OPTIONS="-XX:+UseZGC -XX:+ZGenerational"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
