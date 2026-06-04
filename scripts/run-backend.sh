#!/usr/bin/env bash

# Run TermX Server in local development mode.
# - Runs on port 8200 by default (kills any process already bound to it)
# - Dev authentication (Bearer token: yupi) is enabled automatically when the
#   `dev` environment is in the list.
#
# Usage: ./scripts/run-backend.sh [environments]
#   [environments] = the FULL comma-separated Micronaut environment list to activate;
#                    each loads its application-<env>.yml. Default: dev,local
#   Examples:
#     ./scripts/run-backend.sh                       # dev,local
#     ./scripts/run-backend.sh dev,dev.termx.org     # dev + application-dev.termx.org.yml
#     ./scripts/run-backend.sh dev,local,myflavor    # any combination you want

set -e

# Run from repo root (this script lives in scripts/).
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.."

PORT="${TERMX_SERVER_PORT:-8200}"
ENVS="${1:-dev,local}"

# Helper: kill any process listening on a given TCP port. Pattern lifted from
# ~/source/emr/repo/scripts/run-backend.sh so termx and emr behave the same way
# when an old run was Ctrl-Z'd or a gradle daemon held the port.
kill_port() {
  local port=$1
  local pid
  pid=$(lsof -ti:"$port" 2>/dev/null || true)
  if [ -n "$pid" ]; then
    echo "Killing existing process on port $port (PID $pid)..."
    kill -9 $pid 2>/dev/null || true
    # Give the kernel a moment to release the socket before gradle binds it.
    sleep 1
  fi
}

kill_port "$PORT"

# Resolve whether the Liquibase datasource is enabled for the active environments.
# Mirrors Micronaut precedence: base application.yml first, then each application-<env>.yml
# in list order (environments listed later win). Best-effort read of the YAML property
# `liquibase.datasources.liquibase.enabled` so the banner reflects what the app will do.
RES_DIR="termx-app/src/main/resources"

liquibase_enabled_in_file() {
  local f="$1"
  [ -f "$f" ] || return 0
  awk '
    /^liquibase:[[:space:]]*$/ { inblock=1; next }
    inblock && /^[^[:space:]]/ { inblock=0 }
    inblock && /enabled:[[:space:]]*(true|false)/ {
      v=$0; sub(/.*enabled:[[:space:]]*/, "", v); sub(/[^a-z].*$/, "", v); print v; exit
    }
  ' "$f"
}

LIQUIBASE_ENABLED=""
for e in application ${ENVS//,/ }; do
  if [ "$e" = "application" ]; then f="$RES_DIR/application.yml"; else f="$RES_DIR/application-$e.yml"; fi
  v="$(liquibase_enabled_in_file "$f")"
  [ -n "$v" ] && LIQUIBASE_ENABLED="$v"
done

case "$LIQUIBASE_ENABLED" in
  true)  LIQUIBASE_STATE="enabled" ;;
  false) LIQUIBASE_STATE="DISABLED (migrations will NOT run)" ;;
  *)     LIQUIBASE_STATE="enabled (default)" ;;
esac

echo "=========================================="
echo "Starting TermX Server (Local Dev Mode)"
echo "=========================================="
echo "Environments: ${ENVS}"
echo "Port:         ${PORT}"
echo "Dev Token:    yupi (when 'dev' is in the list)"
echo "Liquibase:    ${LIQUIBASE_STATE}"
echo ""

# Run the application
./gradlew :termx-app:run -Penv="${ENVS}"
