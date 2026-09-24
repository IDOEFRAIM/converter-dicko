package com.converter.settings.service;

import com.converter.audit.domain.AuditAction;
import com.converter.audit.service.AuditService;
import com.converter.common.exception.BusinessException;
import com.converter.settings.domain.SettingKey;
import com.converter.settings.domain.SettingType;
import com.converter.settings.domain.SystemSetting;
import com.converter.settings.repository.SystemSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de {@link SettingsService}, avec un
 * {@link SystemSettingRepository} entierement simule (Mockito) : aucune
 * base de donnees n'est necessaire.
 *
 * <p>Se concentre sur les deux garanties centrales du service : une
 * conversion typee correcte (y compris son rejet propre en cas de
 * donnee corrompue) et l'invalidation du cache a chaque ecriture.
 */
@ExtendWith(MockitoExtension.class)
class SettingsServiceTest {

    @Mock
    private SystemSettingRepository repository;

    @Mock
    private AuditService auditService;

    private SettingsService settingsService;

    @BeforeEach
    void setUp() {
        settingsService = new SettingsService(repository, auditService);
    }

    @Test
    void getDecimal_parsesStoredValue() {
        givenSetting(SettingKey.MIN_ORDER_AMOUNT_CFA, "10000", SettingType.DECIMAL);

        BigDecimal result = settingsService.getDecimal(SettingKey.MIN_ORDER_AMOUNT_CFA);

        assertThat(result).isEqualByComparingTo("10000");
    }

    @Test
    void getInt_parsesStoredValue() {
        givenSetting(SettingKey.RATE_LOCK_DURATION_MINUTES, "30", SettingType.INTEGER);

        assertThat(settingsService.getInt(SettingKey.RATE_LOCK_DURATION_MINUTES)).isEqualTo(30);
    }

    @Test
    void getBoolean_rejectsAnyValueOtherThanTrueOrFalse() {
        // Boolean.parseBoolean transformerait silencieusement "oui" en
        // `false` : ce comportement est explicitement refuse ici, car un
        // drapeau de reservation de tresorerie ne doit jamais echouer en
        // silence.
        givenSetting(SettingKey.TREASURY_RESERVE_ON_ORDER, "oui", SettingType.BOOLEAN);

        assertThatThrownBy(() -> settingsService.getBoolean(SettingKey.TREASURY_RESERVE_ON_ORDER))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void getList_splitsOnCommaAndTrimsEmptyEntries() {
        givenSetting(SettingKey.ENABLED_PAYMENT_METHODS, "MOBILE_MONEY, WAVE ,,BANK_TRANSFER",
                SettingType.STRING);

        assertThat(settingsService.getList(SettingKey.ENABLED_PAYMENT_METHODS))
                .containsExactly("MOBILE_MONEY", "WAVE", "BANK_TRANSFER");
    }

    @Test
    void getString_missingKeyInDatabase_throwsRatherThanReturningNull() {
        // Une cle absente signale une migration incomplete (V4). Le
        // service doit echouer bruyamment plutot que renvoyer null et
        // laisser un appelant financier dereferencer un pointeur nul.
        when(repository.findById(SettingKey.MAX_ORDER_AMOUNT_CFA.name())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> settingsService.getString(SettingKey.MAX_ORDER_AMOUNT_CFA))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void update_invalidatesCache_soNextReadReflectsNewValue() {
        SystemSetting setting = settingOf(SettingKey.MAX_ORDER_AMOUNT_CFA, "2000000", SettingType.DECIMAL);
        when(repository.findById(SettingKey.MAX_ORDER_AMOUNT_CFA.name()))
                .thenReturn(Optional.of(setting));
        when(repository.saveAndFlush(any(SystemSetting.class))).thenReturn(setting);

        // Premiere lecture : peuple le cache avec l'ancienne valeur.
        assertThat(settingsService.getDecimal(SettingKey.MAX_ORDER_AMOUNT_CFA)).isEqualByComparingTo("2000000");

        // `update` mute `setting` en place puis le renvoie via saveAndFlush
        // (mock) : l'objet reference par `setting` porte donc deja la
        // nouvelle valeur des la fin de cet appel.
        UUID actorId = UUID.randomUUID();
        settingsService.update(SettingKey.MAX_ORDER_AMOUNT_CFA, "3000000", actorId);

        assertThat(settingsService.getDecimal(SettingKey.MAX_ORDER_AMOUNT_CFA)).isEqualByComparingTo("3000000");
        verify(repository).saveAndFlush(any(SystemSetting.class));
        // Une modification de parametre metier est desormais tracee dans le journal d'audit
        // (SETTING_UPDATED), avec l'ancienne et la nouvelle valeur en metadonnee.
        verify(auditService).record(eq(actorId), isNull(), eq(AuditAction.SETTING_UPDATED),
                eq("SystemSetting"), eq(SettingKey.MAX_ORDER_AMOUNT_CFA.name()),
                argThat(json -> json.contains("2000000") && json.contains("3000000")));
    }

    @Test
    void update_withNonNumericValueForDecimalSetting_isRejectedBeforeWrite() {
        SystemSetting setting = settingOf(SettingKey.MIN_ORDER_AMOUNT_CFA, "10000", SettingType.DECIMAL);
        when(repository.findById(SettingKey.MIN_ORDER_AMOUNT_CFA.name())).thenReturn(Optional.of(setting));

        assertThatThrownBy(() -> settingsService.update(SettingKey.MIN_ORDER_AMOUNT_CFA, "dix mille",
                UUID.randomUUID()))
                .isInstanceOf(BusinessException.class);

        verify(repository, org.mockito.Mockito.never()).saveAndFlush(any());
    }

    // -----------------------------------------------------------------

    private void givenSetting(SettingKey key, String value, SettingType type) {
        when(repository.findById(key.name())).thenReturn(Optional.of(settingOf(key, value, type)));
    }

    /**
     * Construit un {@link SystemSetting} sans passer par JPA.
     *
     * <p>L'entite n'expose ni constructeur public ni mutateurs de
     * champs autres que {@code updateValue} (deliberement, pour qu'un
     * appelant ne puisse pas modifier {@code settingKey} apres coup) :
     * la reflexion est le seul moyen de preparer un etat de test sans
     * base de donnees.
     */
    private static SystemSetting settingOf(SettingKey key, String value, SettingType type) {
        try {
            var constructor = SystemSetting.class.getDeclaredConstructor();
            // Constructeur `protected`, reserve a JPA : sans ce
            // setAccessible(true), newInstance() leve un
            // IllegalAccessException, independamment de l'accessibilite
            // des champs individuels fixee plus bas.
            constructor.setAccessible(true);
            SystemSetting setting = constructor.newInstance();
            setFieldValue(setting, "settingKey", key.name());
            setFieldValue(setting, "value", value);
            setFieldValue(setting, "valueType", type);
            setFieldValue(setting, "publicSetting", true);
            setFieldValue(setting, "updatedAt", Instant.now());
            return setting;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Impossible de construire un SystemSetting de test", ex);
        }
    }

    private static void setFieldValue(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
