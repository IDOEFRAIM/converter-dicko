package com.converter.settings.service;

import com.converter.audit.domain.AuditAction;
import com.converter.audit.service.AuditService;
import com.converter.common.exception.BusinessException;
import com.converter.common.exception.ErrorCode;
import com.converter.common.exception.ResourceNotFoundException;
import com.converter.common.util.JsonUtil;
import com.converter.settings.domain.SettingKey;
import com.converter.settings.domain.SettingType;
import com.converter.settings.domain.SystemSetting;
import com.converter.settings.dto.PublicSettingsResponse;
import com.converter.settings.dto.SettingResponse;
import com.converter.settings.repository.SystemSettingRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Acces unique aux parametres metier.
 *
 * <p>Aucune borne ni duree n'est ecrite en dur ailleurs dans le code :
 * tout passe par ce service. Un plafond de montant reste donc
 * modifiable par l'administration sans redeploiement.
 *
 * <p>Les lectures sont mises en cache brievement. Ces valeurs sont lues
 * a chaque creation d'ordre mais modifiees quelques fois par an : sans
 * cache, chaque calcul financier declencherait plusieurs allers-retours
 * en base. Le cache est vide des qu'une valeur change, de sorte qu'une
 * modification prend effet immediatement.
 */
@Service
public class SettingsService {

    private static final Logger log = LoggerFactory.getLogger(SettingsService.class);
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final SystemSettingRepository repository;
    private final AuditService auditService;
    private final Cache<SettingKey, String> cache;

    public SettingsService(SystemSettingRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(CACHE_TTL)
                .maximumSize(SettingKey.values().length)
                .build();
    }

    // -----------------------------------------------------------------
    // Lecture typee
    // -----------------------------------------------------------------

    public String getString(SettingKey key) {
        return cache.get(key, this::loadValue);
    }

    public int getInt(SettingKey key) {
        String raw = getString(key);
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            throw misconfigured(key, raw, SettingType.INTEGER);
        }
    }

    public long getLong(SettingKey key) {
        String raw = getString(key);
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            throw misconfigured(key, raw, SettingType.INTEGER);
        }
    }

    public BigDecimal getDecimal(SettingKey key) {
        String raw = getString(key);
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException ex) {
            throw misconfigured(key, raw, SettingType.DECIMAL);
        }
    }

    public boolean getBoolean(SettingKey key) {
        String raw = getString(key).trim();
        // Parse strict : `Boolean.parseBoolean` transforme toute saisie
        // inattendue en `false` silencieux. Sur un drapeau du type
        // TREASURY_RESERVE_ON_ORDER, ce silence couterait cher.
        if ("true".equalsIgnoreCase(raw)) {
            return true;
        }
        if ("false".equalsIgnoreCase(raw)) {
            return false;
        }
        throw misconfigured(key, raw, SettingType.BOOLEAN);
    }

    /** Liste issue d'une valeur separee par des virgules, vides ignorees. */
    public List<String> getList(SettingKey key) {
        return Arrays.stream(getString(key).split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    public Duration getMinutes(SettingKey key) {
        return Duration.ofMinutes(getInt(key));
    }

    // -----------------------------------------------------------------
    // Consultation
    // -----------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<SettingResponse> findAll() {
        return repository.findAllByOrderBySettingKeyAsc().stream()
                .map(SettingsService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PublicSettingsResponse findPublic() {
        return new PublicSettingsResponse(
                getDecimal(SettingKey.MIN_ORDER_AMOUNT_CFA),
                getDecimal(SettingKey.MAX_ORDER_AMOUNT_CFA),
                getInt(SettingKey.RATE_LOCK_DURATION_MINUTES),
                getLong(SettingKey.MAX_PROOF_FILE_SIZE_BYTES),
                getInt(SettingKey.MAX_PROOFS_PER_PAYMENT),
                getList(SettingKey.ENABLED_PAYMENT_METHODS),
                getBoolean(SettingKey.REQUIRE_PAYMENT_PROOF));
    }

    // -----------------------------------------------------------------
    // Modification
    // -----------------------------------------------------------------

    /**
     * Modifie un parametre apres validation de son format.
     *
     * <p>La valeur est verifiee <em>avant</em> ecriture : un plafond
     * saisi en "2 000 000" doit etre refuse a la saisie, pas provoquer
     * une erreur au prochain ordre cree.
     */
    @Transactional
    public SettingResponse update(SettingKey key, String rawValue, UUID actorId) {
        SystemSetting setting = repository.findById(key.name())
                .orElseThrow(() -> ResourceNotFoundException.setting(key.name()));

        String value = rawValue.trim();
        validateFormat(key, value, setting.getValueType());
        String previousValue = setting.getValue();

        setting.updateValue(value, actorId);
        SystemSetting saved = repository.saveAndFlush(setting);

        cache.invalidate(key);
        log.info("Parametre {} modifie par {}", key, actorId);

        // Ces parametres pesent directement sur le pricing et les protections financieres
        // (marge, frais, bornes de montant, moyens de paiement actifs...) : leur modification
        // est une operation sensible, tracee au meme titre qu'une action sur order/payment/treasury.
        auditService.record(actorId, null, AuditAction.SETTING_UPDATED, "SystemSetting", key.name(),
                "{\"previousValue\":" + JsonUtil.jsonString(previousValue) + ",\"newValue\":" + JsonUtil.jsonString(value) + "}");

        return toResponse(saved);
    }

    /** Vide le cache dans son ensemble. Utile aux tests et au demarrage. */
    public void clearCache() {
        cache.invalidateAll();
    }

    // -----------------------------------------------------------------

    private String loadValue(SettingKey key) {
        return repository.findById(key.name())
                .map(SystemSetting::getValue)
                // Une cle absente signifie une migration incomplete :
                // echouer bruyamment vaut mieux qu'une valeur par defaut
                // implicite sur un parametre financier.
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR,
                        "Parametre metier absent en base : " + key.name()));
    }

    private void validateFormat(SettingKey key, String value, SettingType type) {
        boolean valid = switch (type) {
            case STRING -> !value.isBlank();
            case BOOLEAN -> "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
            case INTEGER -> isParsableLong(value);
            case DECIMAL -> isParsableDecimal(value);
        };
        if (!valid) {
            throw new BusinessException(ErrorCode.INVALID_SETTING_VALUE,
                    "Valeur invalide pour " + key.name() + " : type " + type + " attendu.");
        }
        if (type == SettingType.DECIMAL && new BigDecimal(value).signum() < 0) {
            throw new BusinessException(ErrorCode.INVALID_SETTING_VALUE,
                    "Le parametre " + key.name() + " ne peut pas etre negatif.");
        }
    }

    private static boolean isParsableLong(String value) {
        try {
            Long.parseLong(value);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static boolean isParsableDecimal(String value) {
        try {
            new BigDecimal(value);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private BusinessException misconfigured(SettingKey key, String raw, SettingType expected) {
        log.error("Parametre {} illisible : '{}' n'est pas un {}", key, raw, expected);
        return new BusinessException(ErrorCode.INTERNAL_ERROR,
                "Parametre metier mal configure : " + key.name());
    }

    private static SettingResponse toResponse(SystemSetting setting) {
        return new SettingResponse(
                setting.getSettingKey(),
                setting.getValue(),
                setting.getValueType(),
                setting.getDescription(),
                setting.isPublicSetting(),
                setting.getUpdatedAt());
    }
}
