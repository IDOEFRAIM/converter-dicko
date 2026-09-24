#!/usr/bin/env bash
#
# Sauvegarde PostgreSQL — converter-backend.
#
# Principe : pg_dump au format "custom" (compresse nativement, restaurable
# selectivement et en parallele via pg_restore) execute DANS le conteneur
# postgres (meme version que le serveur, aucune dependance a installer sur
# l'hote), puis copie du fichier resultant HORS du conteneur et HORS du
# volume de donnees PostgreSQL (`converter-postgres-data`) : une sauvegarde
# stockee sur le meme volume que les donnees qu'elle protege ne survit pas a
# la perte de ce volume, ce qui la rendrait inutile.
#
# Usage :
#   ./scripts/backup.sh
#
# Variables d'environnement (toutes optionnelles, valeurs par defaut
# alignees sur docker-compose.yml) :
#   POSTGRES_CONTAINER   nom du conteneur PostgreSQL     (defaut: converter-postgres)
#   POSTGRES_DB          nom de la base                  (defaut: converter)
#   POSTGRES_USER         utilisateur PostgreSQL          (defaut: converter)
#   BACKUP_DIR            repertoire de sortie sur l'hote  (defaut: ./backups)
#   BACKUP_RETENTION_DAYS retention en jours               (defaut: 14)

set -euo pipefail

# Evite que Git Bash (MSYS) sur Windows ne traduise les chemins /tmp/... en
# chemins Windows avant qu'ils n'atteignent `docker exec` (sans effet sur
# Linux/macOS, ou cette variable n'est pas interpretee).
export MSYS_NO_PATHCONV=1

POSTGRES_CONTAINER="${POSTGRES_CONTAINER:-converter-postgres}"
POSTGRES_DB="${POSTGRES_DB:-converter}"
POSTGRES_USER="${POSTGRES_USER:-converter}"
BACKUP_DIR="${BACKUP_DIR:-./backups}"
BACKUP_RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-14}"

if ! docker inspect "$POSTGRES_CONTAINER" >/dev/null 2>&1; then
    echo "ERREUR : le conteneur '$POSTGRES_CONTAINER' n'existe pas ou n'est pas demarre." >&2
    exit 1
fi

mkdir -p "$BACKUP_DIR"

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
filename="converter-${POSTGRES_DB}-${timestamp}.dump"
tmp_path_in_container="/tmp/${filename}"
final_path="${BACKUP_DIR}/${filename}"

echo "Sauvegarde de la base '${POSTGRES_DB}' (conteneur '${POSTGRES_CONTAINER}') vers ${final_path} ..."

# Format custom (-F c) : compresse par defaut, restaurable table par table ou
# en parallele avec pg_restore -j, contrairement a un simple dump SQL texte.
docker exec "$POSTGRES_CONTAINER" pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -F c -f "$tmp_path_in_container"

docker cp "${POSTGRES_CONTAINER}:${tmp_path_in_container}" "$final_path"
docker exec "$POSTGRES_CONTAINER" rm -f "$tmp_path_in_container"

size="$(du -h "$final_path" | cut -f1)"
echo "Sauvegarde terminee : ${final_path} (${size})"

# Retention : supprime les sauvegardes de ce script plus vieilles que N jours.
# Ne touche a aucun autre fichier du repertoire.
find "$BACKUP_DIR" -maxdepth 1 -name "converter-${POSTGRES_DB}-*.dump" -mtime "+${BACKUP_RETENTION_DAYS}" -print -delete \
    | sed 's/^/Retention : suppression de /' || true

echo "OK."
