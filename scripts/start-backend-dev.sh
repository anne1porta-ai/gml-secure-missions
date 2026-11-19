#!/usr/bin/env bash
set -euo pipefail

# Lance le backend localement avec PostgreSQL comme base du MVP.
# Les secrets sont chargés depuis .env ou depuis l'environnement. La clé AES
# reste stable entre les redémarrages afin de conserver les données déchiffrables.

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="$ROOT_DIR/.env"

if [[ -f "$ENV_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
fi

export DB_URL="${DB_URL:-jdbc:postgresql://localhost:5432/gmlshield}"
export DB_USER="${DB_USER:-gml}"
export FRONTEND_ORIGIN="http://localhost:5173"
export DEMO_SEED_ENABLED=true
export COOKIE_SECURE=false

missing=()
for name in DB_PASSWORD JWT_SECRET ENCRYPTION_KEY; do
  if [[ -z "${!name:-}" ]]; then
    missing+=("$name")
  fi
done

if (( ${#missing[@]} > 0 )); then
  echo "Variables manquantes: ${missing[*]}. Exécutez ./scripts/create-local-env.sh ou définissez-les avant le lancement." >&2
  exit 1
fi

cd "$ROOT_DIR/backend"
mvn spring-boot:run
