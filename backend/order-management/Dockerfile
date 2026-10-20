# ==========================================
# Backend Dockerfile (Spring Boot 3 / Java 21)
# Assumes pre-built JAR exists under target/
# ==========================================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Install curl for container health check & tzdata for local timezone
RUN apk add --no-cache curl tzdata

# Set restaurant local timezone (Asia/Kolkata)
ENV TZ=Asia/Kolkata

# Run as non-privileged system user for container security
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy pre-built JAR from target/
COPY target/*.jar app.jar

# Expose Spring Boot internal port
EXPOSE 8080

# Production JVM container flags (respect cgroups, heap limits, quick entropy)
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:InitialRAMPercentage=40.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
