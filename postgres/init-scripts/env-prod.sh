#!/usr/bin/env bash

psql -d "postgres" -U "${POSTGRES_USER:-postgres}" -w -f /opt/scripts/create/create_environment_arguments.psql -v env='prod' -v usr=''

[[ "$ENV" == "dev" ]] && /opt/scripts/env-dev.sh || true
