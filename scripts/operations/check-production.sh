#!/usr/bin/env bash
# Contrôle en lecture seule de l'application déployée sur OVH.
set -euo pipefail

usage() {
  echo "Usage : bash scripts/operations/check-production.sh [--local-only]"
}

LOCAL_ONLY=false
if [[ ${1:-} == "--local-only" && $# -eq 1 ]]; then
  LOCAL_ONLY=true
elif (( $# != 0 )); then
  usage >&2
  exit 2
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

command -v docker >/dev/null
command -v curl >/dev/null
docker compose config --quiet

for service in database backend frontend; do
  container_id="$(docker compose ps -q "$service")"
  if [[ -z "$container_id" ]]; then
    echo "ERREUR : conteneur absent pour $service." >&2
    exit 1
  fi

  state="$(docker inspect --format '{{.State.Status}}' "$container_id")"
  health="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$container_id")"
  if [[ "$state" != running ]]; then
    echo "ERREUR : $service est $state." >&2
    exit 1
  fi
  if [[ "$service" != frontend && "$health" != healthy ]]; then
    echo "ERREUR : $service est $health." >&2
    exit 1
  fi
  echo "OK : $service (etat=$state, sante=$health)"
done

# Le service frontend n'a pas de healthcheck Docker dans compose.yaml.
curl --fail --silent --show-error --max-time 15 \
  http://127.0.0.1:8088/ > /dev/null
curl --fail --silent --show-error --max-time 15 \
  http://127.0.0.1:8088/api/auth/csrf > /dev/null
echo "OK : frontend et API accessibles localement."

if [[ "$LOCAL_ONLY" == true ]]; then
  echo "CONTROLE LOCAL REUSSI"
  exit 0
fi

PUBLIC_HOST="${GML_PUBLIC_HOST:-gml-shield-demo.fr}"
PUBLIC_URL="https://$PUBLIC_HOST"

# Vérifie également que le TLS est valide et que l'API fournit un cookie sécurisé.
work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT

curl --fail --silent --show-error --max-time 20 \
  "$PUBLIC_URL/" -o "$work_dir/index.html"
if ! grep -q 'id="root"' "$work_dir/index.html"; then
  echo "ERREUR : le frontend attendu ne répond pas en HTTPS." >&2
  exit 1
fi

curl --fail --silent --show-error --max-time 20 \
  -D "$work_dir/csrf.headers" \
  "$PUBLIC_URL/api/auth/csrf" -o /dev/null
if ! grep -Eiq '^set-cookie: XSRF-TOKEN=.*;.*[;[:space:]]Secure([;[:space:]]|$)' "$work_dir/csrf.headers"; then
  echo "ERREUR : cookie XSRF-TOKEN Secure absent." >&2
  exit 1
fi

# Un proxy SPA peut répondre 200 sur un chemin inconnu : on vérifie le contenu,
# pas uniquement le statut HTTP, pour éviter d'exposer Actuator.
curl --silent --show-error --max-time 20 \
  "$PUBLIC_URL/actuator/health" -o "$work_dir/actuator.txt"
if grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"' "$work_dir/actuator.txt"; then
  echo "ERREUR : l'état interne Actuator est accessible publiquement." >&2
  exit 1
fi

echo "OK : HTTPS, API, cookie sécurisé et endpoint interne non exposé."
echo "CONTROLE PRODUCTION REUSSI"
