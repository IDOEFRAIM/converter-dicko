package com.converter.common.idempotency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Primitives transactionnelles de capture/completion d'une cle d'idempotence.
 *
 * <p>{@link #tryInsert}, {@link #findExisting} et {@link #releasePending} s'executent dans
 * <b>leur propre transaction</b> ({@link Propagation#REQUIRES_NEW}) : la capture d'une cle doit
 * etre visible et durable independamment du sort de la transaction metier qui suit, sans quoi
 * deux requetes concurrentes portant la meme cle pourraient toutes les deux la "gagner".
 * {@link #complete} en revanche est {@code REQUIRED} : il rejoint la transaction de l'action
 * metier pour committer atomiquement avec elle (voir sa Javadoc, passe 2 P2-3).
 *
 * <p><b>Attention a l'auto-invocation</b> : ces methodes ne doivent jamais etre appelees depuis
 * une autre methode de cette meme classe (le proxy Spring serait contourne) — c'est pourquoi
 * l'orchestration se trouve dans une classe separee, {@link IdempotencyGuard}.
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    /**
     * Duree de retention d'une cle. Aucune tache de nettoyage n'existe encore dans cette phase
     * (voir {@code docs/AUDIT_BUSINESS_LOGIC.md} §17) : {@code expires_at} est ecrit pour
     * respecter le schema et preparer un futur job, mais n'est lu par aucun code applicatif.
     */
    static final Duration RETENTION = Duration.ofHours(24);

    private final IdempotencyKeyRepository repository;
    private final Clock clock;

    public IdempotencyService(IdempotencyKeyRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Tente de capturer atomiquement {@code (userId, endpoint, idemKey)}.
     *
     * <p><b>Ne rattrape jamais elle-meme une violation de contrainte</b> : sous PostgreSQL, une
     * erreur SQL (ici {@code uq_idempotency_keys}) marque la transaction JDBC courante comme
     * "aborted" — toute requete ulterieure dans cette meme transaction, y compris une simple
     * lecture, echoue avec {@code current transaction is aborted}. La violation doit donc
     * systematiquement faire echouer <em>cette</em> transaction {@code REQUIRES_NEW} dans son
     * integralite (elle est alors proprement annulee par le framework) ; c'est {@link
     * IdempotencyGuard#guard} qui rattrape l'exception, dans une toute nouvelle transaction/
     * connexion via {@link #findExisting}.
     *
     * @throws org.springframework.dao.DataIntegrityViolationException si la cle est deja capturee
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void tryInsert(UUID userId, String endpoint, String idemKey, String requestHash) {
        Instant now = clock.instant();
        repository.saveAndFlush(new IdempotencyKey(idemKey, userId, endpoint, requestHash, now, now.plus(RETENTION)));
        log.debug("Cle d'idempotence capturee : utilisateur={}, endpoint={}, cle={}", userId, endpoint, idemKey);
    }

    /** Relit une capture existante, dans une transaction neuve — voir {@link #tryInsert}. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<IdempotencyKey> findExisting(UUID userId, String endpoint, String idemKey) {
        return repository.findByUserIdAndEndpointAndIdemKey(userId, endpoint, idemKey);
    }

    /**
     * Memorise le resultat d'une operation dont la capture a reussi.
     *
     * <p><b>Propagation {@code REQUIRED}</b> (et non {@code REQUIRES_NEW}, contrairement aux
     * autres methodes de cette classe) : appele depuis {@link IdempotencyGuard#guard} qui est
     * lui-meme {@code @Transactional}, il <b>rejoint la transaction de l'action metier</b>.
     * L'effet metier (creation d'ordre, credit de tresorerie...) et l'enregistrement de la
     * reponse committent alors atomiquement — ou pas du tout. Cela ferme la fenetre de crash
     * « effet applique mais cle restee <i>pending</i> » (passe 2, P2-3).
     */
    @Transactional
    public void complete(UUID userId, String endpoint, String idemKey, int status, String responseBodyJson) {
        repository.findByUserIdAndEndpointAndIdemKey(userId, endpoint, idemKey).ifPresent(key -> {
            key.complete(status, responseBodyJson);
            repository.save(key);
        });
    }

    /**
     * Supprime une capture non aboutie, pour qu'une nouvelle tentative avec la meme cle ne reste
     * pas bloquee indefiniment apres un echec metier legitime (voir {@link IdempotencyGuard}).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releasePending(UUID userId, String endpoint, String idemKey) {
        repository.deletePending(userId, endpoint, idemKey);
    }
}
