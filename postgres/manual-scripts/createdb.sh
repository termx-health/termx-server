#!/bin/bash

if [ -z "$DB_NAME" ]; then
  echo "parameter missing: DB_NAME"
  exit 1
fi

if [ -z "$USER_PREFIX" ]; then
  USER_PREFIX=$DB_NAME
fi

params=" \
  -v db=$DB_NAME \
  -v admin=${USER_PREFIX}_admin \
  -v admin_pw=${ADMIN_PASSWORD:-test} \
  -v app=${USER_PREFIX}_app \
  -v app_pw=${USER_PASSWORD:-test} \
  -v viewer=${USER_PREFIX}_viewer \
  "

psql -d "${POSTGRES_DB:-postgres}" -U "${POSTGRES_USER:-postgres}" -w -f /opt/scripts/create/create_user.psql $params || exit 1
psql -d "${POSTGRES_DB:-postgres}" -U "${POSTGRES_USER:-postgres}" -w -f /opt/scripts/create/create_database.psql $params || exit 1
psql -d "$DB_NAME" -U "${POSTGRES_USER:-postgres}" -w -f /opt/scripts/create/init_database.psql $params || exit 1
