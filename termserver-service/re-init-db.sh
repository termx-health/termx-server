#!/usr/bin/env bash

docker exec -e "DB_NAME=termserver" -e "USER_PREFIX=termserver" termserver-db /opt/scripts/dropdb.sh
docker exec -e "DB_NAME=termserver" -e "USER_PREFIX=termserver" termserver-db /opt/scripts/createdb.sh

#./gradlew update
