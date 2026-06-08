FROM eclipse-temurin:25-jre

RUN apt-get update && apt-get install -y curl

ARG APP_VERSION
ENV APP_VERSION=${APP_VERSION:-latest}

ARG BUILD_TIME
ENV BUILD_TIME=$BUILD_TIME

# Short commit sha and the merged PR number, surfaced on /info so a running
# container can be traced back to an exact build (version is just the image tag,
# e.g. "latest", which isn't enough on dev).
ARG GIT_COMMIT
ENV GIT_COMMIT=$GIT_COMMIT
ARG PR_NUMBER
ENV PR_NUMBER=$PR_NUMBER

# Fail-fast on OutOfMemoryError instead of letting the JVM limp on with
# a depleted heap. The previous behaviour (no flag) caused the May 2026
# dev-server incident where a single OOM cascaded into every concurrent
# request, the Netty selector, AND the SnomedImportPollingService — all
# died with their own OOMs over several minutes while the container
# kept running. ExitOnOutOfMemoryError makes the JVM exit immediately;
# Docker's `restart: unless-stopped` brings it back clean within seconds.
# HeapDumpOnOutOfMemoryError + HeapDumpPath leave a postmortem .hprof so
# the next incident can be diagnosed without re-OOMing dev-server to
# capture a heap dump live.
#
# /app/logs is the standard volume-mount path the deploy compose files
# already export to /opt/.../logs on the host, so dumps survive container
# recreation. Create it eagerly so the JVM doesn't error if the volume
# isn't bound (dev runs without a mount).
RUN mkdir -p /app/logs

ENV JVM_OOM_OPTS="-XX:+ExitOnOutOfMemoryError -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/app/logs/"

COPY build/libs/*-all.jar terminology.jar
CMD java ${JVM_OOM_OPTS} ${JAVA_OPTS} -jar terminology.jar
