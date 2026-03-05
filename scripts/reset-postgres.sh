#!/usr/bin/env bash
set -euo pipefail

DB_NAME="thesis_repo"
DB_USER="thesis_user"
DB_PASS="thesis_pass"
PG_HOST="127.0.0.1"
PG_PORT="5432"

ensure_command() {
  local cmd="$1"
  if ! command -v "${cmd}" >/dev/null 2>&1; then
    echo "Missing required command: ${cmd}"
    exit 1
  fi
}

start_postgres_if_needed() {
  if pg_isready -h "${PG_HOST}" -p "${PG_PORT}" >/dev/null 2>&1; then
    echo "Postgres is already reachable at ${PG_HOST}:${PG_PORT}."
    return
  fi

  echo "Postgres is not reachable. Attempting to start Homebrew service..."
  if ! command -v brew >/dev/null 2>&1; then
    echo "Homebrew is not installed or not on PATH."
    echo "Start PostgreSQL manually, then rerun this script."
    exit 1
  fi

  if brew services start postgresql@16 >/dev/null 2>&1; then
    echo "Started postgresql@16."
  elif brew services start postgresql >/dev/null 2>&1; then
    echo "Started postgresql."
  else
    echo "Failed to start PostgreSQL via Homebrew."
    echo "Try manually:"
    echo "  brew services start postgresql@16"
    echo "or"
    echo "  brew services start postgresql"
    exit 1
  fi

  sleep 1
  if ! pg_isready -h "${PG_HOST}" -p "${PG_PORT}" >/dev/null 2>&1; then
    echo "Postgres still not reachable at ${PG_HOST}:${PG_PORT}."
    echo "Check status with: brew services list | grep postgres"
    exit 1
  fi
}

run_reset_sql() {
  if ! psql -v ON_ERROR_STOP=1 -d postgres <<SQL
SELECT pg_terminate_backend(pid)
FROM pg_stat_activity
WHERE datname = '${DB_NAME}' AND pid <> pg_backend_pid();

DROP DATABASE IF EXISTS ${DB_NAME};
DROP ROLE IF EXISTS ${DB_USER};
CREATE ROLE ${DB_USER} WITH LOGIN PASSWORD '${DB_PASS}';
CREATE DATABASE ${DB_NAME} OWNER ${DB_USER};
SQL
  then
    echo "Failed to run reset SQL using: psql -d postgres"
    echo "You may need a local PostgreSQL superuser role matching your macOS user."
    echo "Alternatively, connect as a superuser and run this script with that role."
    exit 1
  fi
}

ensure_command psql
ensure_command pg_isready

start_postgres_if_needed
run_reset_sql

echo "Done. Run ./run-postgres.sh (or ./mvnw spring-boot:run) to recreate schema via Flyway."
