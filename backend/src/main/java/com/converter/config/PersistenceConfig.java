package com.converter.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Active le remplissage automatique de {@code createdAt} / {@code updatedAt}
 * sur les entites {@code AuditableEntity} via {@code @CreatedDate} /
 * {@code @LastModifiedDate}.
 *
 * <p>Le fuseau horaire de la JVM est force en UTC des le point d'entree
 * ({@code ConverterApplication}), donc {@code Instant.now()} et les
 * colonnes {@code TIMESTAMPTZ} restent coherents sans conversion.
 */
@Configuration
@EnableJpaAuditing
public class PersistenceConfig {
}
