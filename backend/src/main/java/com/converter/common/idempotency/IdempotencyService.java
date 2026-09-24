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

    /**
     * Recupere les captures restees "en attente" (jamais completees) au-dela de
     * {@code pendingTimeout} — repond au scenario d'un crash serveur survenu entre {@link #tryInsert}
     * (qui a deja commite, dans sa propre transaction) et {@link #complete}/{@link #releasePending}
     * (jamais atteints).
     *
     * <p><b>Preuve que cette suppression ne peut jamais toucher une operation reellement
     * committee</b> : {@link IdempotencyGuard#guard} appelle {@link #complete} avec la propagation
     * par defaut ({@code REQUIRED}) — {@code complete} rejoint donc <em>la meme transaction
     * physique</em> que l'action metier qu'il accompagne. Les deux ne peuvent donc que committer
     * ensemble ou echouer ensemble : il n'existe structurellement aucun etat ou l'action metier a
     * commite pendant que {@code responseStatus} serait reste {@code NULL}. Une ligne encore
     * {@code pending} passe {@code pendingTimeout} ne peut donc signifier qu'une chose : sa
     * transaction ne s'est jamais terminee (crash, ou echec deja rattrape par
     * {@link #releasePending} — auquel cas la ligne n'existe deja plus).
     *
     * <p><b>Limite assumee, non dissimulee</b> : cette preuve garantit qu'aucune transaction
     * <em>committee</em> n'est jamais touchee. Elle ne garantit PAS, en toute rigueur
     * mathematique, qu'aucune transaction n'est encore <em>en cours d'execution</em> au moment de
     * la recuperation — seule une marge de securite tres large (le defaut, {@code
     * idempotency.pending-timeout-ms}, largement superieure a la duree reelle de n'importe quelle
     * des operations gardees, qui n'effectuent aucun I/O externe) rend ce scenario negligeable en
     * pratique. Voir docs/ARCHITECTURE.md pour le detail de ce raisonnement.
     *
     * @return le nombre de captures effectivement recuperees par cet appel
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int reclaimStalePending(Duration pendingTimeout) {
        Instant threshold = clock.instant().minus(pendingTimeout);
        int reclaimed = repository.deleteStalePendingOlderThan(threshold);
        if (reclaimed > 0) {
            log.info("Idempotence : {} cle(s) 'pending' recuperee(s) (plus vieille(s) que {})",
                    reclaimed, pendingTimeout);
        }
        return reclaimed;
    }
}
