FROM openjdk:17.0.1-slim

ARG APP_VERSION
ENV APP_VERSION=${APP_VERSION:-latest}

ARG BUILD_TIME
ENV BUILD_TIME=$BUILD_TIME

COPY build/libs/*-all.jar terminology.jar
CMD java ${JAVA_OPTS} -jar terminology.jar
