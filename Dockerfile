# Spring Boot fat-jar runtime image (avoids Paketo downloading Liberica from GitHub in CI).
FROM eclipse-temurin:21-jre-jammy
# Pick up Ubuntu security fixes (e.g. libssl3 CVE-2026-84782) without waiting on a Temurin rebuild.
RUN apt-get update \
  && apt-get install -y --no-install-recommends --only-upgrade libssl3 openssl ca-certificates \
  && rm -rf /var/lib/apt/lists/*
WORKDIR /app
ARG JAR_FILE=build/libs/*-SNAPSHOT.jar
# Prefer non-SNAPSHOT when tagged builds produce versioned jars
COPY build/libs/*.jar /app/app.jar
ENV JAVA_OPTS=""
EXPOSE 8080
ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar /app/app.jar"]
