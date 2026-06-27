FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /workspace/server
COPY server/pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

COPY server/src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app
RUN addgroup -S app && adduser -S app -G app && mkdir -p /app/storage && chown -R app:app /app
COPY --from=build /workspace/server/target/workforce-hub-0.1.0-SNAPSHOT.jar /app/workforce-hub.jar

USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/workforce-hub.jar"]
