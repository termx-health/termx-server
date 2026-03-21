#!/usr/bin/env bash

# Run TermX Server in local development mode
# This script runs the application with dev profile enabled, which:
# - Enables dev authentication (Bearer token: yupi)
# - Loads application-dev.yml and application-local.yml configurations
# - Runs on port 8200 by default

set -e

# Get the directory where the script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=========================================="
echo "Starting TermX Server (Local Dev Mode)"
echo "=========================================="
echo "Profile: dev,local"
echo "Port: 8200"
echo "Dev Token: yupi"
echo ""

# Run the application
./gradlew :termx-app:run -Pdev
