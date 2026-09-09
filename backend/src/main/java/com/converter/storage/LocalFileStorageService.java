package com.converter.storage;

import com.converter.common.exception.BusinessException;
import com.converter.common.exception.ErrorCode;
import com.converter.config.props.StorageProperties;
import com.converter.storage.exception.StorageException;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Implementation MVP de {@link FileStorageService} : disque local, sous
 * un repertoire racine hors de la racine web.
 *
 * <p>{@code storageKey} est entierement genere ici
 * ({@code {directory}/{annee}/{mois}/{uuid}.{extension}}) — jamais
 * derive du nom fourni par le client, ce qui rend un path traversal
 * structurellement impossible (voir docs/ARCHITECTURE.md, Partie II,
 * section J.2, risque 11).
 */
@Component
public class LocalFileStorageService implements FileStorageService {

    private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("yyyy/MM")
            .withZone(ZoneOffset.UTC);

    private final Path root;

    public LocalFileStorageService(StorageProperties properties) {
        this.root = Path.of(properties.rootDir()).toAbsolutePath().normalize();
    }

    @PostConstruct
    void ensureRootExists() {
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new StorageException("Impossible de creer le repertoire de stockage : " + root, e);
        }
    }

    @Override
    public StoredFile store(String directory, String originalFileName, String contentType, byte[] content) {
        String safeDirectory = directory.replaceAll("[^a-zA-Z0-9-]", "");
        String extension = FileNameSanitizer.extensionFor(contentType);
        String relativeKey = safeDirectory + "/" + YEAR_MONTH.format(Instant.now())
                + "/" + UUID.randomUUID() + "." + extension;

        Path target = resolveWithinRoot(relativeKey);

        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new StorageException("Echec d'ecriture du fichier : " + relativeKey, e);
        }

        return new StoredFile(relativeKey, FileNameSanitizer.sanitizeDisplayName(originalFileName),
                contentType, content.length, sha256Of(content));
    }

    @Override
    public Resource load(String storageKey) {
        Path target = resolveWithinRoot(storageKey);
        if (!Files.isRegularFile(target)) {
            // La ligne de metadonnees existe encore en base mais le fichier a disparu du
            // disque (ex. volume de stockage non persiste entre deux recreations de conteneur).
            // C'est une ressource introuvable cote client (404), pas une panne serveur (500) :
            // renvoyer un code metier clair plutot que de laisser fuir une 500 opaque.
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                    "Preuve indisponible : le fichier n'existe plus sur le serveur.");
        }
        return new FileSystemResource(target);
    }

    @Override
    public void delete(String storageKey) {
        Path target = resolveWithinRoot(storageKey);
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new StorageException("Echec de suppression du fichier : " + storageKey, e);
        }
    }

    /**
     * Resout {@code storageKey} sous {@link #root} et refuse toute
     * resolution qui en sortirait — filet de securite supplementaire,
     * independant du fait que {@code storageKey} est de toute facon
     * toujours genere par cette meme classe, jamais fourni par un
     * appelant exterieur.
     */
    private Path resolveWithinRoot(String storageKey) {
        Path resolved = root.resolve(storageKey).normalize();
        if (!resolved.startsWith(root)) {
            throw new StorageException("Cle de stockage invalide : " + storageKey, null);
        }
        return resolved;
    }

    private static String sha256Of(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible sur cette JVM", e);
        }
    }
}
