FROM node:24-bookworm AS node

RUN corepack enable && corepack prepare pnpm@11.1.2 --activate

FROM eclipse-temurin:25-jdk AS build

RUN apt-get update \
    && apt-get install --no-install-recommends --yes git \
    && rm -rf /var/lib/apt/lists/*
COPY --from=node /usr/local/bin/node /usr/local/bin/node
COPY --from=node /usr/local/bin/npm /usr/local/bin/npm
COPY --from=node /usr/local/bin/npx /usr/local/bin/npx
COPY --from=node /usr/local/bin/corepack /usr/local/bin/corepack
COPY --from=node /usr/local/lib/node_modules /usr/local/lib/node_modules
RUN corepack enable && corepack prepare pnpm@11.1.2 --activate

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
EXPOSE 4567

ENTRYPOINT ["java", "-Duser.home=/data", "-jar", "/app/suwairo-server.jar"]
