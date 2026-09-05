# =====================================================================
#  Image de production du backend Converter.
#
#  Construction en deux etapes :
#    1. "build"   compile le jar avec le Maven Wrapper (JDK 21 complet).
#    2. runtime   ne contient qu'un JRE 21 et le jar construit — aucun
#                 outil de compilation ni source ne survit dans l'image
#                 finale livree, ce qui reduit sa surface d'attaque et
#                 sa taille.
# =====================================================================

# ---------------------------------------------------------------------
# Etape 1 : compilation
# ---------------------------------------------------------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Les fichiers de dependances sont copies avant le code source : tant
# que pom.xml ne change pas, Docker reutilise la couche contenant les
# dependances deja telechargees, meme si le code applicatif change.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

COPY src/ src/
RUN ./mvnw -B clean package -DskipTests

# ---------------------------------------------------------------------
# Etape 2 : image d'execution
# ---------------------------------------------------------------------
FROM eclipse-temurin:21-jre AS runtime

# wget est necessaire au HEALTHCHECK ci-dessous ; l'image de base ne
# l'embarque pas. Nettoyage immediat des listes apt pour ne pas
# alourdir l'image finale.
RUN apt-get update \
    && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/*

# Utilisateur non-root : une compromission de l'application ne donne
# pas directement les privileges root dans le conteneur.
RUN groupadd --system converter && useradd --system --gid converter converter

WORKDIR /app
COPY --from=build /workspace/target/converter-backend-*.jar app.jar
# Repertoire de stockage des preuves (app.storage.root-dir = ./storage) cree
# ici et possede par 'converter' : quand docker-compose monte le volume nomme
# converter-storage sur /app/storage, il herite de cet utilisateur/permissions
# (sinon le point de montage serait root et l'ecriture des preuves echouerait).
RUN mkdir -p /app/storage
RUN chown -R converter:converter /app
USER converter

EXPOSE 8080

# Sonde applicative : attend que Flyway ait termine ses migrations et
# que le contexte Spring soit pleinement initialise, pas seulement que
# le port TCP reponde.
HEALTHCHECK --interval=10s --timeout=5s --start-period=45s --retries=5 \
    CMD wget -q -O - http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
