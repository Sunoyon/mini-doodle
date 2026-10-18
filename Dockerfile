# Build and package the jar
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

COPY gradlew build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle
COPY src/main ./src/main

RUN --mount=type=cache,target=/root/.gradle \
    sh ./gradlew --no-daemon bootJar && \
    cp build/libs/*.jar /workspace/app.jar

# Run the jar with non root user
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S app && adduser -S -G app app
COPY --from=build --chown=app:app /workspace/app.jar ./app.jar

USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
