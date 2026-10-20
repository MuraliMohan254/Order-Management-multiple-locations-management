#!/usr/bin/env bash
# Generates self-signed SSL certificate for OM Order Management
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
cd "$DIR"

echo "Generating self-signed SSL certificate in $DIR..."
openssl req -x509 -nodes -days 3650 -newkey rsa:2048 \
  -keyout OM.key \
  -out OM.crt \
  -config openssl.cnf \
  -extensions v3_req

chmod 600 OM.key 2>/dev/null || true
chmod 644 OM.crt 2>/dev/null || true

echo "SSL Certificate generated successfully:"
echo "  Key:  $DIR/OM.key"
echo "  Cert: $DIR/OM.crt"
