#!/usr/bin/env bash
set -euo pipefail

CONNECTION_URL="postgresql://thesis_user:thesis_pass@127.0.0.1:5432/thesis_repo"

if ! command -v psql >/dev/null 2>&1; then
  echo "psql command not found. Install PostgreSQL client tools first."
  exit 1
fi

echo "Checking PostgreSQL connectivity using thesis_user..."
if psql "${CONNECTION_URL}" -c "select 1;" >/dev/null 2>&1; then
  echo "Connection OK."
  exit 0
fi

echo "Connection failed."
echo "Troubleshooting:"
echo "1) Check service status: ./scripts/postgres-status.sh"
echo "2) Start service: brew services start postgresql@16 (or postgresql)"
echo "3) Reset dev database: ./scripts/reset-postgres.sh"
exit 1
