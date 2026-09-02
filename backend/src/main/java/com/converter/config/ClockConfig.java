package com.converter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Horloge injectable, en UTC.
 *
 * <p>Les services qui raisonnent sur des fenetres de temps courtes et
 * testables (expiration d'un devis a 30 minutes, par exemple) injectent
 * ce {@link Clock} plutot que d'appeler {@code Instant.now()}
 * directement : un test peut alors substituer un {@code Clock.fixed(...)}
 * pour verifier un comportement au bord exact de l'expiration, sans
 * dependre d'un vrai delai d'attente.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }
}
