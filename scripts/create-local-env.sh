#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="$ROOT_DIR/.env"

if [[ -f "$ENV_FILE" ]]; then
  echo ".env existe déjà. Supprimez-le explicitement si vous voulez régénérer les secrets." >&2
  exit 1
fi

command -v openssl >/dev/null 2>&1 || {
  echo "OpenSSL est requis pour générer les secrets locaux." >&2
  exit 1
}

JWT_SECRET="$(openssl rand -base64 48 | tr -d '\n')"
ENCRYPTION_KEY="$(openssl rand -base64 32 | tr -d '\n')"
DB_PASSWORD="$(openssl rand -hex 24)"
ADMIN_PASSWORD="$(openssl rand -base64 18 | tr -d '\n')"

cat > "$ENV_FILE" <<EOV
# Généré localement. Ce fichier est ignoré par Git.
JWT_SECRET=$JWT_SECRET
ENCRYPTION_KEY=$ENCRYPTION_KEY
DB_PASSWORD=$DB_PASSWORD
DEMO_SEED_ENABLED=true
ADMIN_EMAIL=admin@gml.local
ADMIN_PASSWORD=$ADMIN_PASSWORD
COOKIE_SECURE=false
FRONTEND_ORIGIN=http://localhost:8088
EOV

chmod 600 "$ENV_FILE" 2>/dev/null || true
echo "Fichier .env local généré : $ENV_FILE"
echo "Compte administrateur : admin@gml.local (mot de passe : ADMIN_PASSWORD dans .env)"
