# ---- build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

# ---- run ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/target/quarkus-app/ ./
EXPOSE 8080
ENV JAVA_OPTS="-Dquarkus.http.host=0.0.0.0"
CMD ["sh", "-c", "exec java $JAVA_OPTS -jar quarkus-run.jar"]
