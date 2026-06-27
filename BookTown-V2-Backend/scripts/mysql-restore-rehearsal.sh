#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -ne 1 ]; then
  echo "Usage: $0 ./backups/mysql/booktown_YYYYMMDDHHMMSS.sql.gz"
  exit 1
fi

DUMP_FILE="$1"
if [ ! -f "$DUMP_FILE" ]; then
  echo "Dump file not found: $DUMP_FILE"
  exit 1
fi

RESTORE_DB="${MYSQL_RESTORE_CHECK_DATABASE:-booktown_restore_check}"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD is required}"

echo "Creating restore rehearsal database: ${RESTORE_DB}"
docker compose -f compose.prod.yaml exec -T mysql \
  mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" \
  -e "DROP DATABASE IF EXISTS \`${RESTORE_DB}\`; CREATE DATABASE \`${RESTORE_DB}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

echo "Restoring dump into ${RESTORE_DB}"
gzip -dc "$DUMP_FILE" | docker compose -f compose.prod.yaml exec -T mysql \
  mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" "${RESTORE_DB}"

echo "Checking restored tables"
docker compose -f compose.prod.yaml exec -T mysql \
  mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" \
  -e "SHOW TABLES FROM \`${RESTORE_DB}\`;"

echo "Restore rehearsal completed"
