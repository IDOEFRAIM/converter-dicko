package com.converter;

import com.converter.settings.domain.SettingKey;
import com.converter.settings.service.SettingsService;
import com.converter.support.AbstractIntegrationTest;
import com.converter.user.domain.RoleCode;
import com.converter.user.repository.RoleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifie que l'application demarre integralement contre un
 * PostgreSQL neuf : toutes les migrations Flyway (V1 a V6)
 * s'appliquent sans erreur, puis Hibernate valide que chaque entite
 * correspond exactement au schema qu'elles ont cree
 * ({@code spring.jpa.hibernate.ddl-auto=validate}).
 *
 * <p>C'est la garantie la plus large de toute la suite : si une
 * migration est incoherente avec une entite, ou si une migration
 * echoue purement et simplement, ce test — et lui seul — s'arrete des
 * le demarrage du contexte, avant meme d'atteindre un {@code @Test}.
 */
class ApplicationStartupIT extends AbstractIntegrationTest {

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private SettingsService settingsService;

    @Test
    void contextLoads_andMigrationsHaveRunAndSeeded() {
        // Le contexte a demarre : Flyway et Hibernate ont deja valide le
        // schema avant que ce test ne s'execute. Il ne reste qu'a verifier
        // que les donnees d'amorcage attendues sont bien presentes.
        assertThat(roleRepository.findByCode(RoleCode.USER)).isPresent();
        assertThat(roleRepository.findByCode(RoleCode.ADMIN)).isPresent();

        assertThat(settingsService.getDecimal(SettingKey.MIN_ORDER_AMOUNT_CFA)).isEqualByComparingTo("10000");
        assertThat(settingsService.getDecimal(SettingKey.MAX_ORDER_AMOUNT_CFA)).isEqualByComparingTo("2000000");
        assertThat(settingsService.getInt(SettingKey.RATE_LOCK_DURATION_MINUTES)).isEqualTo(30);
    }
}
