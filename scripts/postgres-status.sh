#!/usr/bin/env bash
set -euo pipefail

echo "== Homebrew Postgres services =="
if command -v brew >/dev/null 2>&1; then
  if brew services list | grep -i postgres; then
    :
  else
    echo "No PostgreSQL service entries found in Homebrew services list."
  fi
else
  echo "Homebrew (brew) is not installed or not on PATH."
fi

echo
echo "== pg_isready check =="
if command -v pg_isready >/dev/null 2>&1; then
  if pg_isready -h 127.0.0.1 -p 5432; then
    :
  else
    echo "Postgres is not accepting connections on 127.0.0.1:5432."
  fi
else
  echo "pg_isready command not found. Install PostgreSQL client tools."
fi
