FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /workspace
COPY pom.xml ./
COPY plugin-sdk/pom.xml ./plugin-sdk/pom.xml
COPY plugins/hello-plugin/pom.xml ./plugins/hello-plugin/pom.xml
COPY server/pom.xml ./server/pom.xml

COPY plugin-sdk/src ./plugin-sdk/src
COPY server/src ./server/src
RUN mvn -q -pl server -am -DskipTests package

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app
RUN addgroup -S app && adduser -S app -G app && mkdir -p /app/storage /app/plugins && chown -R app:app /app
COPY --from=build /workspace/server/target/workforce-hub-0.1.0-SNAPSHOT.jar /app/workforce-hub.jar

USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/workforce-hub.jar"]
