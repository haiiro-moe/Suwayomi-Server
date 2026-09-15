# syntax=docker/dockerfile:1.7-labs

FROM node:24-bookworm AS node

RUN corepack enable && corepack prepare pnpm@11.1.2 --activate

FROM eclipse-temurin:25-jdk AS build

COPY --from=node /usr/local/ /usr/local/
RUN rm -f /usr/local/bin/pnpm /usr/local/bin/pnpx \
    && npm install --global pnpm@11.1.2

# Force UTF-8 for the build JVM: template engines (jte) read sources as UTF-8 and fail
# with MalformedInputException if the environment leaks a non-UTF-8 default charset.
ENV GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8 -Dkotlin.daemon.jvm.options=-Xmx3g" \
    LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    PNPM_STORE_DIR=/pnpm/store

WORKDIR /workspace

# Keep the frontend dependency layer independent from WebUI source changes.
COPY WebUI/package.json WebUI/pnpm-lock.yaml WebUI/pnpm-workspace.yaml WebUI/
RUN --mount=type=cache,id=suwairo-pnpm-store,target=/pnpm/store \
    pnpm install --dir WebUI --frozen-lockfile --store-dir=/pnpm/store

COPY . .

ENV WEBUI_REVISION=r3484
RUN --mount=type=cache,id=suwairo-gradle-cache,target=/root/.gradle \
    --mount=type=cache,id=suwairo-pnpm-store,target=/pnpm/store \
    ./gradlew :server:shadowJar --no-daemon

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
