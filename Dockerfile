FROM eclipse-temurin:21-jdk-alpine as builder
WORKDIR /app
COPY . .
RUN apk add --no-cache maven && mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar

# Install additional dependencies for image processing
RUN apk update && apk add --no-cache \
    # LibreOffice for document processing
    libreoffice \
    # ImageMagick for image processing
    imagemagick \
    # For file operations and forensics tools
    exiftool \
    file \
    tzdata \
    && rm -rf /var/cache/apk/*

# Set environment variables
ENV SPRING_PROFILES_ACTIVE=prod
ENV TZ=UTC

# Expose the port the application runs on
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]