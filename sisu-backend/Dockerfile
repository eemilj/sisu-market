# Multi-stage build so `docker compose up` works from a clean checkout with no local JDK.
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /src
COPY pom.xml checkstyle.xml ./
RUN mvn -B -q dependency:go-offline
COPY src/ src/
# Quality gates and tests run in CI; the image build only packages the app.
RUN mvn -B -q package -DskipTests -Dcheckstyle.skip -Dspotless.check.skip=true -Djacoco.skip=true

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /src/target/quarkus-app/lib/ lib/
COPY --from=build /src/target/quarkus-app/*.jar ./
COPY --from=build /src/target/quarkus-app/app/ app/
COPY --from=build /src/target/quarkus-app/quarkus/ quarkus/
USER 1001
EXPOSE 8080
ENV JAVA_OPTS_APPEND="-Djava.util.logging.manager=org.jboss.logmanager.LogManager"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS_APPEND -jar /app/quarkus-run.jar"]
