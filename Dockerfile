FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /app

# Copy pom.xml first for dependency caching
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build jar file
RUN mvn clean package -DskipTests


FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy built jar from stage 1
COPY --from=build /app/target/*.jar app.jar

# Create non-root user for security
RUN addgroup -S app && adduser -S app -G app \
    && chown -R app:app /app

# Switch to non-root user
USER app

# Expose port
EXPOSE 8080

# Run the app
ENTRYPOINT ["java", "-jar", "app.jar"]