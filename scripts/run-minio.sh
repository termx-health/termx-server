#!/usr/bin/env bash
# Start a local Minio container for development. Persists data in a Docker
# volume so restarts preserve uploads. The S3 API is on 9000, the web console
# on 9001. Credentials default to minio / minio123 — override with env vars
# below for any non-local setup.
#
# Defaults line up with what bob/MinioService expects in dev:
#   bob.minio.url        = http://localhost:9000
#   bob.minio.access-key = minio
#   bob.minio.secret-key = minio123
#
# Usage: ./scripts/run-minio.sh

set -euo pipefail

CONTAINER_NAME="${MINIO_CONTAINER_NAME:-tx-minio}"
IMAGE="${MINIO_IMAGE:-minio/minio:latest}"
DATA_VOLUME="${MINIO_DATA_VOLUME:-tx-minio-data}"
API_PORT="${MINIO_API_PORT:-9000}"
CONSOLE_PORT="${MINIO_CONSOLE_PORT:-9001}"
ROOT_USER="${MINIO_ROOT_USER:-minio}"
ROOT_PASSWORD="${MINIO_ROOT_PASSWORD:-minio123}"

echo "Starting Minio container '${CONTAINER_NAME}' on ports ${API_PORT} (API) / ${CONSOLE_PORT} (console)..."

# Remove any existing container with the same name (keep the data volume).
if [ -n "$(docker ps -aq -f name="^${CONTAINER_NAME}$")" ]; then
  echo "Removing existing container '${CONTAINER_NAME}' (volume '${DATA_VOLUME}' is kept)..."
  docker rm -vf "${CONTAINER_NAME}" >/dev/null
fi

docker run -d \
  --restart=unless-stopped \
  --name "${CONTAINER_NAME}" \
  -p "${API_PORT}:9000" \
  -p "${CONSOLE_PORT}:9001" \
  -e "MINIO_ROOT_USER=${ROOT_USER}" \
  -e "MINIO_ROOT_PASSWORD=${ROOT_PASSWORD}" \
  -v "${DATA_VOLUME}:/data" \
  "${IMAGE}" \
  server /data --console-address ":9001"

echo ""
echo "Minio is starting up."
echo "  S3 API   : http://localhost:${API_PORT}"
echo "  Console  : http://localhost:${CONSOLE_PORT}"
echo "  User     : ${ROOT_USER}"
echo "  Password : ${ROOT_PASSWORD}"
echo ""
echo "Configure termx-server with:"
echo "  bob.minio.url=http://localhost:${API_PORT}"
echo "  bob.minio.access-key=${ROOT_USER}"
echo "  bob.minio.secret-key=${ROOT_PASSWORD}"
