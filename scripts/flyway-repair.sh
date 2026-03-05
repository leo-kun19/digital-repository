#!/usr/bin/env bash
set -euo pipefail

DB_URL="${DB_URL:-jdbc:postgresql://localhost:5432/thesis_repo}"
DB_USER="${DB_USER:-thesis_user}"
DB_PASS="${DB_PASS:-thesis_pass}"

echo "Running Flyway repair against: ${DB_URL} (user: ${DB_USER})"
./mvnw -Dflyway.url="${DB_URL}" -Dflyway.user="${DB_USER}" -Dflyway.password="${DB_PASS}" flyway:repair
