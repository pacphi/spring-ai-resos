# Use official OpenJDK image
FROM bellsoft/liberica-openjdk-debian:21.0.7-cds

# Set working directory
WORKDIR /app

# Copy the JAR file into the container
COPY ../target/spring-ai-resos-backend-0.0.1-SNAPSHOT.jar app.jar

# Expose the port your app runs on
EXPOSE 8080

# Run the jar file
ENTRYPOINT ["java", \
    "-Djava.security.egd=file:///dev/urandom", \
    "-XX:+UseZGC", \
    "-XX:+UseStringDeduplication", \
    "-jar", "/app/app.jar"]
