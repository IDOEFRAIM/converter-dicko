#!/usr/bin/env bash
#
# Deploiement Converter — PostgreSQL + backend + frontend, via docker compose.
#
# Ce script est volontairement mince : il ne fait que ce qu'un operateur
# ferait a la main (build + up + attente de sante), mais il REFUSE de
# demarrer en profil `prod` si un secret obligatoire manque, plutot que de
# laisser le backend echouer 45 s plus tard dans les journaux.
#
# Usage :
#   ./scripts/deploy.sh [--pull] [--no-build]
#
#   --pull       rafraichit les images de base (postgres, node, nginx) avant build
#   --no-build   ne reconstruit pas les images (redemarrage simple)
#
# Pre-requis : Docker + plugin `docker compose` v2. Un fichier `.env` a la
# racine (copie de `.env.example`, renseigne). Runbook complet : DEPLOY.md.

set -euo pipefail

# Git Bash (MSYS) sur Windows : empeche la traduction des chemins.
export MSYS_NO_PATHCONV=1

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

PULL=0
BUILD=1
for arg in "$@"; do
  case "$arg" in
    --pull) PULL=1 ;;
    --no-build) BUILD=0 ;;
    -h|--help) sed -n '2,20p' "$0"; exit 0 ;;
    *) echo "Argument inconnu : $arg" >&2; exit 2 ;;
  esac
done

# --- Outils -----------------------------------------------------------
command -v docker >/dev/null 2>&1 || { echo "ERREUR : docker introuvable." >&2; exit 1; }
docker compose version >/dev/null 2>&1 || { echo "ERREUR : le plugin 'docker compose' v2 est requis." >&2; exit 1; }

# On fige la liste des fichiers compose : docker-compose.override.yml est une
# commodite de DEV (remappe le port hote de PostgreSQL) et ne doit pas
# s'appliquer en deploiement.
COMPOSE=(docker compose -f docker-compose.yml)

# --- .env -----------------------------------------------------------
if [[ ! -f .env ]]; then
  cp .env.example .env
  echo "Un fichier .env vient d'etre cree depuis .env.example."
  echo "Renseignez-le (au minimum les secrets en profil prod) puis relancez ce script."
  exit 1
fi

set -a
# shellcheck disable=SC1091
. ./.env
set +a

PROFILE="${SPRING_PROFILES_ACTIVE:-dev}"
echo ">> Profil applicatif : $PROFILE"

# --- Garde-fous prod ----------------------------------------------
if [[ "$PROFILE" == "prod" ]]; then
  missing=()
  [[ -n "${JWT_SECRET:-}" ]] || missing+=("JWT_SECRET (openssl rand -base64 48)")
  if [[ -n "${JWT_SECRET:-}" && ${#JWT_SECRET} -lt 32 ]]; then
    missing+=("JWT_SECRET trop court (${#JWT_SECRET} caracteres, minimum 32)")
  fi
  [[ -n "${POSTGRES_PASSWORD:-}" || -n "${DB_PASSWORD:-}" ]] || missing+=("POSTGRES_PASSWORD")
  if [[ "${ADMIN_SEED_ENABLED:-false}" == "true" && -z "${ADMIN_PASSWORD:-}" ]]; then
    missing+=("ADMIN_PASSWORD (ADMIN_SEED_ENABLED=true : un mot de passe aleatoire est refuse en prod)")
  fi
  if (( ${#missing[@]} > 0 )); then
    echo "ERREUR : configuration prod incomplete dans .env :" >&2
    printf '  - %s\n' "${missing[@]}" >&2
    exit 1
  fi
fi

# --- Build & up -------------------------------------------------------
if (( PULL )); then
  echo ">> Rafraichissement des images de base..."
  "${COMPOSE[@]}" pull --ignore-buildable || true
fi

if (( BUILD )); then
  echo ">> Construction des images..."
  "${COMPOSE[@]}" build
fi

echo ">> Demarrage des services..."
"${COMPOSE[@]}" up -d

# --- Attente de sante ----------------------------------------------
wait_healthy() {
  local name="$1" timeout="${2:-180}" waited=0 status
  printf ">> Attente de %s " "$name"
  while (( waited < timeout )); do
    status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}no-healthcheck{{end}}' "$name" 2>/dev/null || echo "absent")"
    case "$status" in
      healthy) echo " OK"; return 0 ;;
      absent) echo " (conteneur absent)"; return 1 ;;
    esac
    printf '.'
    sleep 3
    waited=$((waited + 3))
  done
  echo " TIMEOUT (${timeout}s, dernier etat : ${status:-inconnu})"
  return 1
}

ok=1
wait_healthy converter-postgres 90  || ok=0
wait_healthy converter-backend  240 || ok=0
wait_healthy converter-frontend 120 || ok=0

echo
if (( ! ok )); then
  echo "ECHEC : un service n'est pas sain. Diagnostic :" >&2
  "${COMPOSE[@]}" ps
  echo "--- backend (50 dernieres lignes) ---"
  "${COMPOSE[@]}" logs --tail=50 backend || true
  exit 1
fi

echo "=================================================================="
echo " Deploiement OK"
echo "   Frontend : http://localhost:${FRONTEND_PORT:-4300}"
echo "   API      : http://localhost:${SERVER_PORT:-8080}/actuator/health"
if [[ "$PROFILE" != "prod" ]]; then
  echo
  echo " Mot de passe admin genere (profil $PROFILE, si seed active) :"
  "${COMPOSE[@]}" logs backend 2>/dev/null | grep -i -E "mot de passe|password" | tail -3 || echo "   (non trouve — voir 'docker compose logs backend')"
fi
echo
echo " Pensez a planifier une sauvegarde : scripts/backup.sh (cron)."
echo "=================================================================="
