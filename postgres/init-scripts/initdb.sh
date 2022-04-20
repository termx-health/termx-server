#!/usr/bin/env bash

[[ -z $PGDATA ]] && PGDATA=/var/lib/postgresql/data/pgdata

psql -d "${POSTGRES_DB:-postgres}" -U "${POSTGRES_USER:-postgres}" -w -f /docker-entrypoint-initdb.d/init-conf.psql || exit 1


