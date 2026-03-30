#!/usr/bin/env bash

set -euo pipefail

CONTAINER_NAME="${CONTAINER_NAME:-termx-postgres}"
POSTGRES_USER="${POSTGRES_USER:-postgres}"
DB_NAME="${1:-termserver_new}"
DB_OWNER="${DB_OWNER:-termserver_admin}"
APP_USER="${APP_USER:-termserver_app}"
ADMIN_PASSWORD="${DB_ADMIN_PASSWORD:-test}"
APP_PASSWORD="${DB_APP_PASSWORD:-test}"

echo "Recreating database '${DB_NAME}' in container '${CONTAINER_NAME}'..."

docker exec -i "${CONTAINER_NAME}" psql -v ON_ERROR_STOP=1 -U "${POSTGRES_USER}" <<EOSQL
DO \$\$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${DB_OWNER}') THEN
    EXECUTE 'CREATE ROLE ${DB_OWNER} LOGIN PASSWORD ''${ADMIN_PASSWORD}'' NOSUPERUSER INHERIT NOCREATEDB CREATEROLE NOREPLICATION';
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${APP_USER}') THEN
    EXECUTE 'CREATE ROLE ${APP_USER} LOGIN PASSWORD ''${APP_PASSWORD}'' NOSUPERUSER INHERIT NOCREATEDB CREATEROLE NOREPLICATION';
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'termserver_viewer') THEN
    EXECUTE 'CREATE ROLE termserver_viewer NOLOGIN NOSUPERUSER INHERIT NOCREATEDB NOCREATEROLE NOREPLICATION';
  END IF;
END
\$\$;

SELECT pg_terminate_backend(pid)
FROM pg_stat_activity
WHERE datname = '${DB_NAME}'
  AND pid <> pg_backend_pid();

DROP DATABASE IF EXISTS ${DB_NAME};
CREATE DATABASE ${DB_NAME} WITH OWNER = ${DB_OWNER} ENCODING = 'UTF8' TABLESPACE = pg_default CONNECTION LIMIT = -1;
GRANT TEMP ON DATABASE ${DB_NAME} TO ${APP_USER};
GRANT CONNECT ON DATABASE ${DB_NAME} TO ${APP_USER};
EOSQL

docker exec -i "${CONTAINER_NAME}" psql -v ON_ERROR_STOP=1 -U "${POSTGRES_USER}" -d "${DB_NAME}" <<EOSQL
CREATE EXTENSION IF NOT EXISTS hstore SCHEMA public;
EOSQL

echo "Database '${DB_NAME}' is ready."
#echo "Example:"
#echo "  DB_URL=jdbc:postgresql://localhost:5432/${DB_NAME} ./gradlew :termx-app:run -Pdev"
