package com.converter.config.props;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransFiPropertiesTest {

    @Test
    void defaultProperties_areNeitherConfiguredNorActive() {
        TransFiProperties properties = new TransFiProperties(false, null, null, null, null);

        assertThat(properties.configured()).isFalse();
        assertThat(properties.active()).isFalse();
        assertThat(properties.webhookConfigured()).isFalse();
    }

    @Test
    void configured_withAllCredentials_isTrue() {
        TransFiProperties properties = new TransFiProperties(false, "https://api.transfi.test", "client-id",
                "client-secret", null);

        assertThat(properties.configured()).isTrue();
    }

    @Test
    void configured_withMissingCredential_isFalse() {
        assertThat(new TransFiProperties(false, "https://api.transfi.test", "", "client-secret", null)
                .configured()).isFalse();
        assertThat(new TransFiProperties(false, "https://api.transfi.test", "client-id", null, null)
                .configured()).isFalse();
        assertThat(new TransFiProperties(false, null, "client-id", "client-secret", null)
                .configured()).isFalse();
    }

    @Test
    void active_requiresBothEnabledAndConfigured() {
        TransFiProperties configuredButDisabled = new TransFiProperties(false, "https://api.transfi.test",
                "client-id", "client-secret", null);
        assertThat(configuredButDisabled.active()).isFalse();

        TransFiProperties enabledAndConfigured = new TransFiProperties(true, "https://api.transfi.test",
                "client-id", "client-secret", null);
        assertThat(enabledAndConfigured.active()).isTrue();

        TransFiProperties enabledButNotConfigured = new TransFiProperties(true, null, null, null, null);
        assertThat(enabledButNotConfigured.active()).isFalse();
    }

    @Test
    void webhookConfigured_requiresNonBlankSecret() {
        assertThat(new TransFiProperties(false, null, null, null, "wh-secret").webhookConfigured()).isTrue();
        assertThat(new TransFiProperties(false, null, null, null, "  ").webhookConfigured()).isFalse();
    }
}
