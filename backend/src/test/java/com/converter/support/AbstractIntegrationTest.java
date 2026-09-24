package com.converter.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.security.SecureRandom;

/**
 * Socle commun de tous les tests d'integration.
 *
 * <p>Un seul conteneur PostgreSQL est demarre pour l'ensemble de la
 * suite, via un bloc d'initialisation statique plutot que via
 * {@code @Testcontainers}/{@code @Container} : ces annotations
 * pilotent un cycle de vie <em>par classe de test</em> et, meme sur un
 * champ {@code static}, peuvent arreter puis recreer le conteneur
 * entre deux classes qui heritent de cette base — chaque classe repart
 * alors avec un nouveau conteneur sur un nouveau port, alors que le
 * contexte Spring, lui, reste mis en cache avec l'ancienne URL JDBC,
 * desormais injoignable. Un bloc {@code static} garantit au contraire
 * que l'initialisation n'a lieu qu'une seule fois par classloader,
 * quel que soit le nombre de sous-classes qui declenchent le
 * chargement de cette classe abstraite — c'est le pattern "conteneur
 * singleton" documente par Testcontainers pour un partage inter-classes.
 *
 * <p>{@code @ServiceConnection} (Spring Boot 3.1+) configure
 * automatiquement {@code spring.datasource.*} vers le conteneur des
 * que celui-ci est demarre : plus besoin de
 * {@code @DynamicPropertySource} manuel.
 *
 * <p>Aucun test ne depend d'un PostgreSQL installe localement — seule
 * une machine Docker fonctionnelle est requise, ce que
 * {@code ./mvnw test} verifie de la meme facon en local et en CI.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    static {
        POSTGRES.start();
        // Aucun POSTGRES.stop() explicite : le conteneur doit survivre
        // jusqu'a la fin de la JVM de test pour rester partage par
        // toutes les classes. Son arret est delegue a Ryuk (le
        // conteneur de nettoyage de Testcontainers), qui le supprime
        // automatiquement des que cette JVM se termine ou se deconnecte
        // du daemon Docker.
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    protected TestRestTemplate restTemplate;

    /**
     * Numero de telephone unique par appel, au format E.164 valide.
     *
     * <p>La base de test est partagee entre toutes les methodes de test
     * de la JVM (contrainte {@code uq_users_phone}) : chaque test qui
     * cree un compte doit utiliser un numero qui ne collisionne avec
     * aucun autre test.
     */
    protected static String uniquePhone() {
        long suffix = Math.abs(RANDOM.nextLong() % 100_000_000L);
        return String.format("+225%09d", suffix);
    }
}
