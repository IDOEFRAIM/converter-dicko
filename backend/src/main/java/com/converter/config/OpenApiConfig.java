package com.converter.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadonnees et schema de securite de la documentation Swagger.
 *
 * <p>Le schema {@code bearer-jwt} permet a l'interface Swagger de
 * porter le jeton sur chaque appel de test, et documente pour tout
 * lecteur qu'un endpoint prive exige {@code Authorization: Bearer <jwt>}.
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearer-jwt";

    @Bean
    public OpenAPI converterOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Converter API")
                        .description("Plateforme d'echange CFA (XOF) <-> Yuan (CNY)")
                        .version("v0.1 (Phase 2 - fondations)")
                        .contact(new Contact().name("Converter")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME_NAME,
                        new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
