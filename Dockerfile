# Multi-stage build for production-ready Kody-Salesforce Integration
FROM maven:3.9.4-eclipse-temurin-11 AS builder

# Set working directory
WORKDIR /app

# Copy Maven files for dependency caching
COPY pom.xml .
COPY src/main/proto/ src/main/proto/

# Download dependencies (cached layer if pom.xml hasn't changed)
RUN mvn dependency:go-offline -B

# Copy source code (bust cache if needed by adding timestamp comment)
# Last updated: 2025-08-04 11:30
COPY src/ src/

# Force clean build - remove any cached build artifacts
RUN rm -rf ~/.m2/repository/org/example/pubsub-java

# Build the application
RUN mvn clean package -DskipTests -B

# Production runtime image
FROM eclipse-temurin:11-jre

# Install dumb-init for proper signal handling (Ubuntu-based image)
RUN apt-get update && apt-get install -y --no-install-recommends dumb-init && \
    rm -rf /var/lib/apt/lists/*

# Create non-root user for security
RUN groupadd kody && \
    useradd -r -g kody kody

# Set working directory
WORKDIR /app

# Copy built JAR from builder stage
COPY --from=builder /app/target/pubsub-java-*.jar app.jar

# Create directories for configuration and logs
RUN mkdir -p config logs && \
    chown -R kody:kody /app

# Switch to non-root user
USER kody

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD pgrep -f "java.*app.jar" || exit 1

# Default environment variables
ENV JAVA_OPTS="-XX:+UnlockExperimentalVMOptions -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0" \
    KODY_ENV=sandbox \
    LOG_LEVEL=INFO

# Expose ports (if needed for monitoring/metrics)
EXPOSE 8080

# Use dumb-init as PID 1 for proper signal handling
ENTRYPOINT ["dumb-init", "--"]

# Default command - can be overridden
CMD ["sh", "-c", "java $JAVA_OPTS -cp app.jar genericpubsub.KodyPaymentSubscriber $KODY_ENV"]