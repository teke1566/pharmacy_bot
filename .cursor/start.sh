#!/usr/bin/env bash
# Per-boot startup for the tenahub-bot Cloud Agent environment.
# Starts PostgreSQL and ensures the tenahub role/database exist. Idempotent.
set -euo pipefail

# Detect the installed PostgreSQL cluster version (e.g. 16).
PG_VER="$(ls /etc/postgresql 2>/dev/null | sort -V | tail -n1 || true)"
if [ -z "$PG_VER" ]; then
  echo "PostgreSQL is not installed; run .cursor/install.sh first." >&2
  exit 1
fi

# Start the cluster only if it is not already accepting connections.
if ! pg_isready -q -h localhost -p 5432; then
  sudo pg_ctlcluster "$PG_VER" main start
fi

# Wait for readiness.
for _ in $(seq 1 30); do
  pg_isready -q -h localhost -p 5432 && break
  sleep 1
done
pg_isready -h localhost -p 5432

# Provision role + database idempotently (matches application.yml.example).
sudo -u postgres psql -tAc "SELECT 1 FROM pg_roles WHERE rolname='tenahub'" | grep -q 1 \
  || sudo -u postgres psql -c "CREATE ROLE tenahub LOGIN PASSWORD 'change-me';"
sudo -u postgres psql -tAc "SELECT 1 FROM pg_database WHERE datname='tenahub'" | grep -q 1 \
  || sudo -u postgres psql -c "CREATE DATABASE tenahub OWNER tenahub;"

echo "PostgreSQL ready on localhost:5432 (db=tenahub, user=tenahub)."
