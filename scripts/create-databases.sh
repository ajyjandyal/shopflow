#!/usr/bin/env bash
# Creates the three per-service databases in your LOCAL PostgreSQL (Homebrew install).
# Safe to run more than once: existing databases are skipped.
set -euo pipefail
cd "$(dirname "$0")/.."

if [ ! -f .env ]; then echo "ERROR: .env not found in $(pwd). Copy .env.example to .env first."; exit 1; fi
set -a; source .env; set +a

: "${DB_USERNAME:?DB_USERNAME missing in .env}"
: "${DB_PASSWORD:?DB_PASSWORD missing in .env}"

if ! psql postgres -tAc "SELECT 1 FROM pg_roles WHERE rolname='${DB_USERNAME}'" | grep -q 1; then
  psql postgres -c "CREATE USER ${DB_USERNAME} WITH PASSWORD '${DB_PASSWORD}';"
fi

for db in shopflow_users shopflow_products shopflow_orders; do
  if psql postgres -tAc "SELECT 1 FROM pg_database WHERE datname='${db}'" | grep -q 1; then
    echo "Database ${db} already exists (skipped)"
  else
    psql postgres -c "CREATE DATABASE ${db} OWNER ${DB_USERNAME};"
  fi
done
echo "Done."
