#!/usr/bin/env bash
#
# Restauration PostgreSQL — converter-backend.
#
# ATTENTION : DESTRUCTIF sur la base cible. `--clean --if-exists` supprime
# les objets existants avant de les recreer depuis la sauvegarde. Ce script
# refuse de s'executer sans le flag --yes explicite : aucune commande
# destructive ne doit pouvoir s'executer par accident ou par script tiers.
#
# Usage :
#   ./scripts/restore.sh <fichier.dump> --yes [nom-conteneur-cible]
#
# Le conteneur cible DOIT deja exister et exposer un serveur PostgreSQL
# (voir docs/RUNBOOK.md, scenario "Restore PostgreSQL", pour la procedure
# complete incluant la creation de l'instance cible). Ce script ne cree pas
# lui-meme le conteneur : forcer la creation d'une instance cible en dur ici
# empecherait de reutiliser ce script aussi bien pour un test de restauration
# (Phase 6) que pour une restauration reelle vers l'instance de production.
#
# Variables d'environnement (optionnelles) :
#   POSTGRES_DB    nom de la base cible       (defaut: converter)
#   POSTGRES_USER  utilisateur PostgreSQL     (defaut: converter)

set -euo pipefail

# Voir backup.sh : evite la traduction de chemin MSYS sur Git Bash/Windows.
export MSYS_NO_PATHCONV=1

BACKUP_FILE="${1:-}"
CONFIRM_FLAG="${2:-}"
TARGET_CONTAINER="${3:-converter-postgres}"

POSTGRES_DB="${POSTGRES_DB:-converter}"
POSTGRES_USER="${POSTGRES_USER:-converter}"

if [ -z "$BACKUP_FILE" ] || [ "$CONFIRM_FLAG" != "--yes" ]; then
    echo "Usage : $0 <fichier.dump> --yes [nom-conteneur-cible]" >&2
    echo "Le flag --yes est obligatoire : cette operation est destructive sur la base cible." >&2
    exit 1
fi

if [ ! -f "$BACKUP_FILE" ]; then
    echo "ERREUR : fichier de sauvegarde introuvable : $BACKUP_FILE" >&2
    exit 1
fi

if ! docker inspect "$TARGET_CONTAINER" >/dev/null 2>&1; then
    echo "ERREUR : le conteneur cible '$TARGET_CONTAINER' n'existe pas ou n'est pas demarre." >&2
    exit 1
fi

filename="$(basename "$BACKUP_FILE")"
tmp_path_in_container="/tmp/${filename}"

echo "Restauration de ${BACKUP_FILE} vers '${POSTGRES_DB}' (conteneur '${TARGET_CONTAINER}') ..."

docker cp "$BACKUP_FILE" "${TARGET_CONTAINER}:${tmp_path_in_container}"

# --clean --if-exists : ramene la base cible a l'etat exact de la sauvegarde,
# meme si elle contient deja des objets (ex. schema cree par Flyway au
# premier demarrage d'une instance cible neuve).
docker exec "$TARGET_CONTAINER" pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
    --clean --if-exists --no-owner --no-privileges -v "$tmp_path_in_container"

docker exec "$TARGET_CONTAINER" rm -f "$tmp_path_in_container"

echo "OK. Verifier ensuite manuellement les tables (voir docs/RUNBOOK.md, scenario Restore PostgreSQL)."
