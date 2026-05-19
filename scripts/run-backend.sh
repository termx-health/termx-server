#!/usr/bin/env bash

# Run TermX Server in local development mode
# This script runs the application with dev profile enabled, which:
# - Enables dev authentication (Bearer token: yupi)
# - Loads application-dev.yml and application-local.yml configurations
# - Runs on port 8200 by default
# - Kills any process already bound to that port so consecutive starts don't
#   fall back to a different port or wedge gradle on "Address already in use"

set -e

# Run from repo root (this script lives in scripts/).
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.."

PORT="${TERMX_SERVER_PORT:-8200}"

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
echo "Profile: dev,local"
echo "Port:    ${PORT}"
echo "Dev Token: yupi"
echo ""

# Run the application
./gradlew :termx-app:run -Pdev
