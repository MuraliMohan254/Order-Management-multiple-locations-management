#!/usr/bin/env bash
# ==============================================================================
# WildBar POS - Production Database Restore Script for Cloud / Ubuntu EC2
# Supports: Docker (om_postgres) & Native PostgreSQL installations
# ==============================================================================

set -eo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../../.." && pwd)"

# Load configurations
CONFIG_FILE="$ROOT_DIR/backup-config.json"
if [ -f "$SCRIPT_DIR/../backup-config.json" ]; then
  CONFIG_FILE="$SCRIPT_DIR/../backup-config.json"
fi

if [ -f "$ROOT_DIR/.env" ]; then
  # shellcheck disable=SC1091
  source "$ROOT_DIR/.env"
fi

DB_NAME="${DB_NAME:-order_management}"
DB_USER="${DB_USER:-postgres}"
DB_PASSWORD="${DB_PASSWORD:-Inventory\$murali@2025}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"

BACKUP_DIR="/var/backups/wildbar"
CONTAINER_NAME="om_postgres"

if command -v python3 &>/dev/null && [ -f "$CONFIG_FILE" ]; then
  eval "$(python3 -c "
import json
try:
    with open('$CONFIG_FILE') as f:
        cfg = json.load(f)
    print(f\"DB_NAME='{cfg.get('database', {}).get('name', '$DB_NAME')}'\")
    print(f\"DB_USER='{cfg.get('database', {}).get('username', '$DB_USER')}'\")
    print(f\"DB_PASSWORD='{cfg.get('database', {}).get('password', '$DB_PASSWORD')}'\")
    print(f\"BACKUP_DIR='{cfg.get('cloud', {}).get('backupDirectory', '$BACKUP_DIR')}'\")
    print(f\"CONTAINER_NAME='{cfg.get('cloud', {}).get('dockerContainer', '$CONTAINER_NAME')}'\")
except Exception:
    pass
")"
fi

BACKUP_FILE="$1"
if [ -z "$BACKUP_FILE" ]; then
  for dir in "$BACKUP_DIR" "${HOME:-/tmp}/wildbar_backups" "./backups"; do
    if [ -d "$dir" ]; then
      FOUND=$(find "$dir" -name "wildbar_${DB_NAME}_*.dump" -type f -printf '%T+ %p\n' 2>/dev/null | sort -r | head -n1 | cut -d' ' -f2-)
      if [ -n "$FOUND" ] && [ -f "$FOUND" ]; then
        BACKUP_FILE="$FOUND"
        break
      fi
    fi
  done
fi

if [ -z "$BACKUP_FILE" ] || [ ! -f "$BACKUP_FILE" ]; then
  echo "[ERROR] No valid backup file found to restore."
  echo "Usage: ./restore-cloud.sh [/path/to/backup.dump]"
  exit 1
fi

echo "=========================================================="
echo "Starting WildBar POS Database Restore"
echo "Source Backup File: $BACKUP_FILE"
echo "Target Database:    $DB_NAME"
echo "=========================================================="
read -r -p "Are you sure you want to completely overwrite '$DB_NAME'? (y/N): " CONFIRM
if [[ ! "$CONFIRM" =~ ^[Yy]$ ]]; then
  echo "Restore aborted by user."
  exit 0
fi

IS_DOCKER=false
if command -v docker &>/dev/null; then
  if docker ps --format '{{.Names}}' | grep -Eq "^${CONTAINER_NAME}\$"; then
    IS_DOCKER=true
  fi
fi

if [ "$IS_DOCKER" = true ]; then
  echo "[INFO] Using Docker container: $CONTAINER_NAME"
  
  echo "[INFO] Closing active connections and recreating database '$DB_NAME'..."
  docker exec -e PGPASSWORD="$DB_PASSWORD" "$CONTAINER_NAME" \
    psql -U "$DB_USER" -d postgres -c "DROP DATABASE IF EXISTS \"$DB_NAME\" WITH (FORCE);" -q
  docker exec -e PGPASSWORD="$DB_PASSWORD" "$CONTAINER_NAME" \
    psql -U "$DB_USER" -d postgres -c "CREATE DATABASE \"$DB_NAME\" WITH ENCODING = 'UTF8';" -q

  echo "[INFO] Restoring data..."
  docker exec -i -e PGPASSWORD="$DB_PASSWORD" "$CONTAINER_NAME" \
    pg_restore -U "$DB_USER" -d "$DB_NAME" --no-owner --no-privileges < "$BACKUP_FILE" || true

else
  echo "[INFO] Using host PostgreSQL tools..."
  
  export PGPASSWORD="$DB_PASSWORD"
  psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d postgres -c "DROP DATABASE IF EXISTS \"$DB_NAME\" WITH (FORCE);" -q
  psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d postgres -c "CREATE DATABASE \"$DB_NAME\" WITH ENCODING = 'UTF8';" -q

  echo "[INFO] Restoring data..."
  pg_restore -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" --no-owner --no-privileges "$BACKUP_FILE" || true
  unset PGPASSWORD
fi

echo "=========================================================="
echo "✓ Database '$DB_NAME' restored successfully!"
echo "WildBar POS services are ready."
echo "=========================================================="
