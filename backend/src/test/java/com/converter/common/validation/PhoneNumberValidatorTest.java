package com.converter.common.validation;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneNumberValidatorTest {

    private final PhoneNumberValidator validator = new PhoneNumberValidator();

    @ParameterizedTest
    @ValueSource(strings = {
            "+2250700000000",
            "+225070000000",
            "+8613800138000",
            "+33123456789"
    })
    void acceptsValidE164Numbers(String phone) {
        assertThat(validator.isValid(phone, mockContext())).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "0700000000",          // sans indicatif international
            "225 07 00 00 00 00",  // espaces non normalises
            "+0700000000",         // indicatif commencant par zero
            "+22507",               // trop court
            "abc",
            ""
    })
    void rejectsInvalidNumbers(String phone) {
        assertThat(validator.isValid(phone, mockContext())).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "'+225 07 00 00 00 00', '+2250700000000'",
            "'+225.07.00.00.00.00', '+2250700000000'",
            "'+225-07-00-00-00-00', '+2250700000000'",
            "'(+225)0700000000',    '+2250700000000'"
    })
    void normalizeStripsSeparators(String raw, String expected) {
        assertThat(PhoneNumberValidator.normalize(raw)).isEqualTo(expected);
    }

    @org.junit.jupiter.api.Test
    void nullIsConsideredValid_presenceIsNotBlanksResponsibility() {
        // @NotBlank porte deja la verification de presence : le validateur
        // de format ne doit pas produire un second message d'erreur pour
        // la meme absence de valeur.
        assertThat(validator.isValid(null, mockContext())).isTrue();
    }

    private ConstraintValidatorContext mockContext() {
        return org.mockito.Mockito.mock(ConstraintValidatorContext.class);
    }
}
