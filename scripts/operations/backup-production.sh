#!/usr/bin/env bash
# Archive PostgreSQL et les documents du projet Compose existant, sans redémarrage.
set -euo pipefail
umask 077

usage() {
  echo "Usage : bash scripts/operations/backup-production.sh [--with-env] [--output-dir DOSSIER]"
}

INCLUDE_ENV=false
OUTPUT_DIR="$HOME/GMLSHIELD-backups"

while (( $# > 0 )); do
  case "$1" in
    --with-env)
      INCLUDE_ENV=true
      shift
      ;;
    --output-dir)
      if (( $# < 2 )); then usage >&2; exit 2; fi
      OUTPUT_DIR="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      usage >&2
      exit 2
      ;;
  esac
done

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

for cmd in docker tar sha256sum gzip; do
  command -v "$cmd" >/dev/null
done

test -f .env || { echo "ERREUR : fichier .env absent." >&2; exit 1; }
docker compose config --quiet

mkdir -p -m 700 "$OUTPUT_DIR"
OUTPUT_DIR="$(cd "$OUTPUT_DIR" && pwd -P)"
work_dir="$(mktemp -d "$OUTPUT_DIR/.save.XXXXXXXX")"
partial_archive=""
finish() {
  code=$?
  if [[ -n "$partial_archive" ]]; then rm -f -- "$partial_archive"; fi
  rm -rf -- "$work_dir"
  exit "$code"
}
trap finish EXIT

# La redirection stdin est indispensable dans un script exécuté à distance en SSH.
docker compose exec -T database \
  pg_dump -U gml -d gmlshield --format=custom --no-owner --no-acl < /dev/null \
  > "$work_dir/postgres.dump"
test -s "$work_dir/postgres.dump"
docker compose exec -T database pg_restore --list < "$work_dir/postgres.dump" > /dev/null

docker compose exec -T backend \
  tar -czf - -C /app/storage/documents . < /dev/null \
  > "$work_dir/documents.tar.gz"
tar -tzf "$work_dir/documents.tar.gz" > /dev/null

components=(postgres.dump documents.tar.gz)
if [[ "$INCLUDE_ENV" == true ]]; then
  cp -- .env "$work_dir/production.env"
  chmod 600 "$work_dir/production.env"
  components+=(production.env)
fi

(cd "$work_dir" && sha256sum -- "${components[@]}" > SHA256SUMS)
components+=(SHA256SUMS)

stamp="$(date -u +%Y%m%dT%H%M%SZ)"
archive="$OUTPUT_DIR/GMLSHIELD-$stamp.tar.gz"
partial_archive="$archive.part"
tar -C "$work_dir" -czf "$partial_archive" -- "${components[@]}"
tar -tzf "$partial_archive" >/dev/null
mv -- "$partial_archive" "$archive"
partial_archive=""

(cd "$OUTPUT_DIR" && sha256sum -- "$(basename "$archive")" > "$(basename "$archive").sha256")
chmod 600 -- "$archive" "$archive.sha256"

echo "Sauvegarde créée : $archive"
echo "Empreinte SHA-256 : $archive.sha256"
if [[ "$INCLUDE_ENV" == true ]]; then
  echo "ATTENTION : l'archive contient les secrets de .env ; chiffrer avant copie externe."
else
  echo "INFO : .env n'est pas sauvegardé. Garder une copie chiffrée des clés séparément."
fi
echo "ATTENTION : base et fichiers sont capturés successivement, sans arrêt des écritures."
