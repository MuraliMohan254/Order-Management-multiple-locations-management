#!/usr/bin/env bash
# ==============================================================================
# WildBar POS - Production Database Backup Script for Cloud / Ubuntu EC2
# Supports: Docker (om_postgres) & Native PostgreSQL installations
# ==============================================================================

set -eo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../../.." && pwd)"

# 1. Load Configurations (Environment .env first, then backup-config.json)
CONFIG_FILE="$ROOT_DIR/backup-config.json"
if [ -f "$SCRIPT_DIR/../backup-config.json" ]; then
  CONFIG_FILE="$SCRIPT_DIR/../backup-config.json"
fi

# Load from .env if present
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
MAX_BACKUPS=14
RETENTION_DAYS=14
S3_BUCKET=""

# Try reading from backup-config.json if python3 / jq is available
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
    print(f\"S3_BUCKET='{cfg.get('cloud', {}).get('s3Bucket', '')}'\")
    print(f\"MAX_BACKUPS={cfg.get('retention', {}).get('maxBackupsToKeep', 14)}\")
    print(f\"RETENTION_DAYS={cfg.get('retention', {}).get('retentionDays', 14)}\")
except Exception:
    pass
")"
fi

# Ensure backup directory is writable (fallback if non-root)
if mkdir -p "$BACKUP_DIR" 2>/dev/null && [ -w "$BACKUP_DIR" ]; then
  : # Writable
else
  FALLBACK_DIR="${HOME:-/tmp}/wildbar_backups"
  echo "[WARN] Directory '$BACKUP_DIR' is not writable. Falling back to '$FALLBACK_DIR' to avoid permission failure."
  BACKUP_DIR="$FALLBACK_DIR"
  mkdir -p "$BACKUP_DIR"
fi
LOG_FILE="$BACKUP_DIR/backup_history.log"

log() {
  local msg="[$(date '+%Y-%m-%d %H:%M:%S')] [$1] $2"
  echo -e "$msg"
  echo "$msg" >> "$LOG_FILE" 2>/dev/null || true
}

log "INFO" "=========================================================="
log "INFO" "Starting Cloud Database Backup for WildBar POS"
log "INFO" "Target Database: $DB_NAME"

DATE_STAMP="$(date '+%Y-%m-%d_%H%M%S')"
BACKUP_FILENAME="wildbar_${DB_NAME}_${DATE_STAMP}.dump"
BACKUP_FILEPATH="$BACKUP_DIR/$BACKUP_FILENAME"

# 2. Check if Docker container is running or use native pg_dump
IS_DOCKER=false
if command -v docker &>/dev/null; then
  if docker ps --format '{{.Names}}' | grep -Eq "^${CONTAINER_NAME}\$"; then
    IS_DOCKER=true
  fi
fi

log "INFO" "Dumping database to: $BACKUP_FILEPATH"

if [ "$IS_DOCKER" = true ]; then
  log "INFO" "Using Docker container: $CONTAINER_NAME"
  docker exec -e PGPASSWORD="$DB_PASSWORD" "$CONTAINER_NAME" \
    pg_dump -U "$DB_USER" -d "$DB_NAME" -F c -b > "$BACKUP_FILEPATH"
else
  log "INFO" "Using host pg_dump client"
  if ! command -v pg_dump &>/dev/null; then
    log "ERROR" "pg_dump command not found on host and Docker container $CONTAINER_NAME not running."
    exit 1
  fi
  PGPASSWORD="$DB_PASSWORD" pg_dump -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -F c -b > "$BACKUP_FILEPATH"
fi

if [ -s "$BACKUP_FILEPATH" ]; then
  FILE_SIZE=$(du -h "$BACKUP_FILEPATH" | cut -f1)
  log "SUCCESS" "✓ Database backup created successfully ($FILE_SIZE): $BACKUP_FILENAME"
else
  log "ERROR" "Backup file is empty or missing!"
  rm -f "$BACKUP_FILEPATH"
  exit 1
fi

# 3. Optional AWS S3 Mirroring
if [ -n "$S3_BUCKET" ] && command -v aws &>/dev/null; then
  log "INFO" "Mirroring backup to AWS S3: s3://$S3_BUCKET/$BACKUP_FILENAME"
  if aws s3 cp "$BACKUP_FILEPATH" "s3://$S3_BUCKET/$BACKUP_FILENAME"; then
    log "SUCCESS" "✓ Successfully synced to AWS S3"
  else
    log "WARN" "Failed to mirror to AWS S3"
  fi
fi

# 4. Retention Policy Cleanup
log "INFO" "Applying retention policy (Keep max $MAX_BACKUPS, older than $RETENTION_DAYS days)..."

# Delete older than RETENTION_DAYS
find "$BACKUP_DIR" -name "wildbar_${DB_NAME}_*.dump" -type f -mtime +"$RETENTION_DAYS" -exec rm -f {} + 2>/dev/null || true

# Enforce count limit
CURRENT_COUNT=$(find "$BACKUP_DIR" -name "wildbar_${DB_NAME}_*.dump" -type f | wc -l)
if [ "$CURRENT_COUNT" -gt "$MAX_BACKUPS" ]; then
  EXCESS=$(( CURRENT_COUNT - MAX_BACKUPS ))
  log "INFO" "Found $CURRENT_COUNT backups (limit: $MAX_BACKUPS). Pruning oldest $EXCESS backups..."
  find "$BACKUP_DIR" -name "wildbar_${DB_NAME}_*.dump" -type f -printf '%T+ %p\n' | sort | head -n "$EXCESS" | cut -d' ' -f2- | xargs -r rm -f
fi

log "INFO" "Cloud backup routine finished successfully."
log "INFO" "=========================================================="
