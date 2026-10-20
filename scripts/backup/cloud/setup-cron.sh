#!/usr/bin/env bash
# ==============================================================================
# Installs a system cron job on Ubuntu / Debian EC2 instances for WildBar POS
# ==============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKUP_SCRIPT="$SCRIPT_DIR/backup-cloud.sh"
CRON_FILE="/etc/cron.d/wildbar-backup"

if [ "$EUID" -ne 0 ]; then
  SUDO='sudo'
else
  SUDO=''
fi

chmod +x "$BACKUP_SCRIPT"

echo "=========================================================="
echo "Setting up Nightly Automated Backup Cron on Linux"
echo "Script: $BACKUP_SCRIPT"
echo "=========================================================="

# Default schedule: Daily at 2:00 AM
SCHEDULE="${1:-0 2 * * *}"

$SUDO bash -c "cat <<EOF > $CRON_FILE
# WildBar POS Automated Nightly Backup
SHELL=/bin/bash
PATH=/usr/local/sbin:/usr/local/bin:/sbin:/bin:/usr/sbin:/usr/bin
$SCHEDULE root $BACKUP_SCRIPT >> /var/log/wildbar-backup.log 2>&1
EOF"

$SUDO chmod 644 "$CRON_FILE"

echo "✓ Cron job registered successfully in $CRON_FILE"
echo "Schedule: $SCHEDULE (Daily at 2:00 AM server time)"
echo "Logs will be written to: /var/log/wildbar-backup.log"
echo "=========================================================="
