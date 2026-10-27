FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml ./
COPY src/main ./src/main
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app app \
    && mkdir -p /app/uploads && chown app:app /app/uploads
COPY --from=build /build/target/backend-0.0.1-SNAPSHOT.jar /app/backend.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/backend.jar"]
