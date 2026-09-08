# Build context must be the repo root (docker-compose.yml sets this) - warden-standalone
# depends on warden-core, which is a sibling module, not something this Dockerfile can see on
# its own with a warden-standalone-only build context.
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /build
COPY pom.xml .
COPY warden-core/pom.xml warden-core/pom.xml
COPY warden-bukkit/pom.xml warden-bukkit/pom.xml
COPY warden-velocity/pom.xml warden-velocity/pom.xml
COPY warden-standalone/pom.xml warden-standalone/pom.xml
COPY warden-core/src warden-core/src
COPY warden-standalone/src warden-standalone/src
# Only warden-standalone + warden-core (-am) actually get built - never touches paper-api or
# velocity-api, so a plain JDK 21 here is correct, unlike warden-bukkit/warden-velocity's builds.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -pl warden-standalone -am clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S warden && adduser -S warden -G warden
WORKDIR /app
COPY --from=build /build/warden-standalone/target/warden-standalone.jar app.jar
RUN mkdir -p /data && chown -R warden:warden /data /app
USER warden
VOLUME /data
EXPOSE 8095
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD wget -q -O - http://127.0.0.1:8095/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
