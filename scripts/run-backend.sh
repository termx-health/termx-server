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

echo "=========================================="
echo "Starting TermX Server (Local Dev Mode)"
echo "=========================================="
echo "Environments: ${ENVS}"
echo "Port:         ${PORT}"
echo "Dev Token:    yupi (when 'dev' is in the list)"
echo ""

# Run the application
./gradlew :termx-app:run -Penv="${ENVS}"
