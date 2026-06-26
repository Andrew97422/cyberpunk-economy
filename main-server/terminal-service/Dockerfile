# syntax=docker/dockerfile:1
# BuildKit cache-mount keeps Maven's ~/.m2 across builds (shared by all services),
# and the deps layer is cached until pom.xml changes — code edits rebuild in seconds.
FROM maven:3.9.8-eclipse-temurin-17 AS builder
WORKDIR /build
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar
EXPOSE 8086
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
