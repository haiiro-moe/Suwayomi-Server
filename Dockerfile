FROM node:24-bookworm AS node

RUN corepack enable && corepack prepare pnpm@11.1.2 --activate

FROM eclipse-temurin:25-jdk AS build

COPY --from=node /usr/local/ /usr/local/
RUN rm -f /usr/local/bin/pnpm /usr/local/bin/pnpx \
    && npm install --global pnpm@11.1.2

ENV GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.jvmargs=-Xmx3g -Dkotlin.daemon.jvm.options=-Xmx3g"

WORKDIR /workspace
COPY . .

ENV WEBUI_REVISION=r3484
RUN ./gradlew :server:shadowJar --no-daemon

FROM eclipse-temurin:25-jre

RUN apt-get update \
    && apt-get install --no-install-recommends --yes ca-certificates wget \
    && rm -rf /var/lib/apt/lists/*

ENV HOME=/data \
    TZ=Etc/UTC

RUN useradd --create-home suwairo

WORKDIR /app
COPY --from=build /workspace/server/build/Suwayomi-Server-*.jar /app/suwairo-server.jar

RUN mkdir -p /data && chown -R suwairo:suwairo /app /data

USER suwairo
VOLUME ["/data"]
VOLUME ["/branding"]
EXPOSE 4567

ENTRYPOINT ["java", "-Duser.home=/data", "-jar", "/app/suwairo-server.jar"]
