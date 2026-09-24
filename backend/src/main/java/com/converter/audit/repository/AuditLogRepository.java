package com.converter.audit.repository;

import com.converter.audit.domain.AuditAction;
import com.converter.audit.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    /**
     * Toutes les entrees d'audit d'une entite precise, de la plus recente a la plus ancienne.
     *
     * <p>Ajout additif de l'evolution Burkina &lt;-&gt; Chine (Phase 8) : requete derivee, sans
     * aucun parametre optionnel. Elle n'est <b>pas</b> une variante de {@link #search} et ne la
     * remplace pas — {@code search} reste la recherche paginee multi-critere de
     * {@code /api/admin/audit-logs}. Ce finder cible le seul besoin des verifications d'audit de
     * bout en bout : « une action a-t-elle bien ete tracee pour CETTE entite ? », avec un
     * {@code entityId} toujours concret. Il evite ainsi le patron {@code (:p IS NULL OR ...)} de
     * {@code search}, dont un parametre dont l'unique occurrence syntaxique est {@code :p IS NULL}
     * n'a pas de type inferable par PostgreSQL au moment du Parse (limitation de {@code search}
     * documentee dans le rapport de cloture Phase 8, hors perimetre de correction de cette phase).
     */
    List<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, String entityId);

    @Query("""
            SELECT a FROM AuditLog a
            WHERE (:actorId IS NULL OR a.actorId = :actorId)
              AND (:action IS NULL OR a.action = :action)
              AND (:entityType IS NULL OR a.entityType = :entityType)
              AND (:entityId IS NULL OR a.entityId = :entityId)
              AND (:from IS NULL OR a.createdAt >= :from)
              AND (:to IS NULL OR a.createdAt <= :to)
            ORDER BY a.createdAt DESC
            """)
    Page<AuditLog> search(@Param("actorId") UUID actorId,
                          @Param("action") AuditAction action,
                          @Param("entityType") String entityType,
                          @Param("entityId") String entityId,
                          @Param("from") Instant from,
                          @Param("to") Instant to,
                          Pageable pageable);
}
