package com.converter.audit.service;

import com.converter.audit.domain.AuditAction;
import com.converter.audit.domain.AuditLog;
import com.converter.audit.dto.AuditLogResponse;
import com.converter.audit.repository.AuditLogRepository;
import com.converter.common.api.PageResponse;
import com.converter.common.util.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Point d'ecriture unique du journal d'audit.
 *
 * <p>Chaque methode d'ecriture s'execute dans <b>sa propre transaction</b>
 * ({@link Propagation#REQUIRES_NEW}), pour deux raisons symetriques :
 * <ul>
 *   <li>un echec d'audit (contrainte, connexion) ne doit jamais faire
 *       echouer l'operation metier qui l'a declenche ;</li>
 *   <li>un rollback de l'operation metier ne doit pas effacer la trace
 *       de la tentative — savoir qu'une validation de paiement a
 *       echoue est aussi precieux que de savoir qu'elle a reussi.</li>
 * </ul>
 * L'echec d'ecriture est rattrape et journalise localement plutot que
 * propage.
 *
 * <p><b>Attention a l'auto-invocation.</b> {@code @Transactional} repose
 * sur un proxy Spring : un appel {@code record(...)} depuis
 * {@code recordSystem(...)} au sein de la meme classe contournerait ce
 * proxy et desactiverait silencieusement {@code REQUIRES_NEW}. Les deux
 * methodes sont donc annotees et implementees independamment, sans
 * appel interne de l'une vers l'autre.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID actorId, String actorPhone, AuditAction action,
                       String entityType, String entityId, String metadataJson) {
        persist(actorId, actorPhone, action, entityType, entityId, metadataJson);
    }

    /** Variante systeme, pour les actions declenchees par un job planifie. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSystem(AuditAction action, String entityType, String entityId, String metadataJson) {
        persist(null, null, action, entityType, entityId, metadataJson);
    }

    private void persist(UUID actorId, String actorPhone, AuditAction action,
                         String entityType, String entityId, String metadataJson) {
        try {
            AuditLog entry = new AuditLog(
                    actorId,
                    actorPhone,
                    action,
                    entityType,
                    entityId,
                    metadataJson,
                    RequestContext.clientIp(),
                    RequestContext.userAgent(),
                    Instant.now());
            repository.save(entry);
        } catch (Exception ex) {
            // Volontairement absorbe : voir le contrat de classe ci-dessus.
            log.error("Echec d'ecriture du journal d'audit pour l'action {}", action, ex);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> search(UUID actorId, AuditAction action,
                                                 String entityType, String entityId,
                                                 Instant from, Instant to, Pageable pageable) {
        Page<AuditLog> page = repository.search(actorId, action, entityType, entityId, from, to, pageable);
        return PageResponse.from(page, AuditService::toResponse);
    }

    private static AuditLogResponse toResponse(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getActorId(),
                log.getActorPhone(),
                log.getAction(),
                log.getEntityType(),
                log.getEntityId(),
                log.getMetadata(),
                log.getIpAddress(),
                log.getCreatedAt());
    }
}
