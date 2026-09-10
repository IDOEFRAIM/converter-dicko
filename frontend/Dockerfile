# =====================================================================
#  Image de production du frontend Converter (Angular 22).
#
#  Deux etapes :
#    1. "build"   compile l'application avec Node 22 (npm ci + ng build).
#    2. runtime   nginx alpine sert les fichiers statiques et relaie /api
#                 vers le conteneur backend (voir nginx.conf).
#
#  Aucune variable de build : l'URL de l'API est `/api` (meme origine),
#  resolue a l'execution par le proxy nginx -> `backend:8080`.
# =====================================================================

# ---------------------------------------------------------------------
# Etape 1 : compilation Angular
# ---------------------------------------------------------------------
FROM node:22-alpine AS build
WORKDIR /app

# package-lock.json copie avant le reste : la couche `npm ci` est
# reutilisee tant que les dependances ne changent pas.
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY . .
# `ng build` -> configuration `production` par defaut (voir angular.json) ;
# sortie : dist/frontend/browser.
RUN npm run build

# ---------------------------------------------------------------------
# Etape 2 : service statique
# ---------------------------------------------------------------------
FROM nginx:1.27-alpine AS runtime

# Remplace la conf par defaut par la notre (SPA + proxy /api).
RUN rm -f /etc/nginx/conf.d/default.conf
COPY nginx.conf /etc/nginx/conf.d/app.conf

COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html

EXPOSE 80

# BusyBox wget est present dans l'image nginx:alpine. 127.0.0.1 explicite
# (pas `localhost`) pour ne pas dependre de l'ordre de resolution v4/v6.
HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=5 \
    CMD wget -q -O /dev/null http://127.0.0.1:80/ || exit 1
