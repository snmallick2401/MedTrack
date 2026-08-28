# Stage 1: Build Java 25 Spring Boot Application
FROM maven:3.9-eclipse-temurin-25 AS builder
WORKDIR /app

# Cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Compile and package application
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Minimal Java 25 JRE Runtime
FROM eclipse-temurin:25-jre
WORKDIR /app

# Copy packaged JAR from builder stage
COPY --from=builder /app/target/medtrack-backend-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080
ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]