package com.converter.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Numero de telephone au format international E.164.
 *
 * <p>Le numero est l'identifiant de connexion : imposer un format
 * unique evite qu'un meme abonne cree deux comptes en saisissant
 * 0700000000 puis +2250700000000. La contrainte est doublee en base
 * par ck_users_phone_format.
 */
@Documented
@Constraint(validatedBy = PhoneNumberValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface PhoneNumber {

    String message() default
            "Numero invalide : format international attendu, par exemple +2250700000000";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
