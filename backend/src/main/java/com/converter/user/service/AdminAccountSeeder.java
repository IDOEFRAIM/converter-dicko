package com.converter.user.service;

import com.converter.common.validation.PhoneNumberValidator;
import com.converter.config.props.AdminSeedProperties;
import com.converter.user.domain.Role;
import com.converter.user.domain.RoleCode;
import com.converter.user.domain.User;
import com.converter.user.repository.RoleRepository;
import com.converter.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.regex.Pattern;

/**
 * Amorce un unique compte administrateur au demarrage, si aucun
 * n'existe encore.
 *
 * <p>Aucun mot de passe n'est ecrit dans le code ni dans un fichier
 * versionne. Le comportement differe selon que {@code ADMIN_PASSWORD}
 * est fourni :
 * <ul>
 *   <li><b>Fourni</b> — utilise tel quel (attendu en production).</li>
 *   <li><b>Absent, profil {@code dev}</b> — un mot de passe aleatoire
 *       de 20 caracteres est genere et affiche <i>une seule fois</i>
 *       dans les journaux de demarrage, jamais persiste en clair.</li>
 *   <li><b>Absent, profil {@code prod}</b> — le demarrage echoue :
 *       generer silencieusement un mot de passe administrateur en
 *       production serait une faille, pas une commodite.</li>
 * </ul>
 *
 * <p>S'execute apres l'initialisation complete du contexte Spring, donc
 * apres l'application des migrations Flyway (V1 a V6) : les tables
 * {@code users} et {@code roles} existent et {@code roles} est deja
 * amorcee (V3) au moment ou ce composant s'execute.
 */
@Component
public class AdminAccountSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountSeeder.class);

    private static final int GENERATED_PASSWORD_LENGTH = 20;
    private static final String PASSWORD_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%^&*-_";
    private static final Pattern E164 = Pattern.compile("^\\+[1-9][0-9]{7,14}$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminSeedProperties properties;
    private final Environment environment;

    public AdminAccountSeeder(UserRepository userRepository,
                              RoleRepository roleRepository,
                              PasswordEncoder passwordEncoder,
                              AdminSeedProperties properties,
                              Environment environment) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            log.info("Amorcage administrateur desactive (ADMIN_SEED_ENABLED=false).");
            return;
        }

        if (userRepository.countByRole(RoleCode.ADMIN) > 0) {
            log.info("Un compte administrateur existe deja : amorcage ignore.");
            return;
        }

        String phone = requirePhone();

        if (userRepository.existsByPhone(phone)) {
            // Un compte USER existe deja sur ce numero : refuser plutot que
            // de promouvoir silencieusement un compte client en administrateur.
            throw new IllegalStateException(
                    "ADMIN_PHONE (" + phone + ") correspond deja a un compte existant non administrateur.");
        }

        SeedPassword seedPassword = resolvePassword();

        Role adminRole = roleRepository.findByCode(RoleCode.ADMIN)
                .orElseThrow(() -> new IllegalStateException(
                        "Role ADMIN absent en base : la migration V3__seed_roles.sql n'a pas ete appliquee."));

        User admin = new User(
                phone,
                passwordEncoder.encode(seedPassword.value()),
                blankToDefault(properties.firstName(), "System"),
                blankToDefault(properties.lastName(), "Administrator"));
        admin.addRole(adminRole);
        userRepository.save(admin);

        if (seedPassword.generated()) {
            logGeneratedPasswordBanner(phone, seedPassword.value());
        } else {
            log.info("Compte administrateur cree : {} (mot de passe fourni via ADMIN_PASSWORD).", phone);
        }
    }

    private String requirePhone() {
        if (properties.phone() == null || properties.phone().isBlank()) {
            throw new IllegalStateException(
                    "ADMIN_PHONE est obligatoire lorsque ADMIN_SEED_ENABLED=true.");
        }
        String normalized = PhoneNumberValidator.normalize(properties.phone());
        if (!E164.matcher(normalized).matches()) {
            throw new IllegalStateException(
                    "ADMIN_PHONE doit respecter le format international E.164, ex. +2250700000000.");
        }
        return normalized;
    }

    private SeedPassword resolvePassword() {
        if (properties.hasExplicitPassword()) {
            return new SeedPassword(properties.password(), false);
        }
        if (environment.matchesProfiles("prod")) {
            // Aucune generation silencieuse en production : un mot de passe
            // administrateur devine par personne n'est jamais acceptable
            // pour un compte a privileges complets.
            throw new IllegalStateException(
                    "ADMIN_PASSWORD est obligatoire en profil prod des lors que "
                            + "ADMIN_SEED_ENABLED=true. Definissez la variable d'environnement "
                            + "avant de redemarrer, ou desactivez le seed.");
        }
        return new SeedPassword(generateSecurePassword(), true);
    }

    private void logGeneratedPasswordBanner(String phone, String password) {
        log.warn("=================================================================");
        log.warn(" COMPTE ADMINISTRATEUR AMORCE (profil de developpement)");
        log.warn(" Telephone     : {}", phone);
        log.warn(" Mot de passe  : {}", password);
        log.warn(" Ce mot de passe n'est affiche qu'une seule fois et n'est stocke");
        log.warn(" nulle part en clair. Notez-le ou changez-le apres connexion.");
        log.warn("=================================================================");
    }

    private static String generateSecurePassword() {
        StringBuilder builder = new StringBuilder(GENERATED_PASSWORD_LENGTH);
        for (int i = 0; i < GENERATED_PASSWORD_LENGTH; i++) {
            builder.append(PASSWORD_ALPHABET.charAt(RANDOM.nextInt(PASSWORD_ALPHABET.length())));
        }
        return builder.toString();
    }

    private static String blankToDefault(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }

    private record SeedPassword(String value, boolean generated) {
    }
}
