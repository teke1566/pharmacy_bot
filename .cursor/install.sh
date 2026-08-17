#!/usr/bin/env bash
# Idempotent repository bootstrap for the tenahub-bot Cloud Agent environment.
# Runs after the repository is checked out. Must terminate and be safe to re-run.
set -euo pipefail

cd "$(dirname "$0")/.."

# --- System dependency: PostgreSQL ---------------------------------------
# tenahub-bot uses PostgreSQL in the default profile. Install it here so it is
# baked into the environment build snapshot. apt is idempotent; the guard keeps
# repeat runs fast.
if ! command -v pg_ctlcluster >/dev/null 2>&1; then
  sudo apt-get update
  sudo DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
    postgresql postgresql-contrib
fi

# --- Local runtime config ------------------------------------------------
# The real application.yml is gitignored. Derive it from the committed example
# so the app can boot in the default (PostgreSQL) profile. The example points at
# db=tenahub / user=tenahub, which the start script provisions.
if [ ! -f src/main/resources/application.yml ]; then
  cp src/main/resources/application.yml.example src/main/resources/application.yml
fi

# --- Warm the Maven cache and compile ------------------------------------
# Downloads dependencies + plugins and compiles main/test sources so later
# runs (spring-boot:run, mvnw test) start quickly. Tests are skipped here; they
# run in H2 and are exercised separately.
./mvnw -B -ntp -DskipTests clean package

echo "install.sh complete."
