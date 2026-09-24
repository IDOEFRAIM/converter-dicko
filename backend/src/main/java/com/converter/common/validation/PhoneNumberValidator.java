package com.converter.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

/** Implementation de {@link PhoneNumber}. */
public class PhoneNumberValidator implements ConstraintValidator<PhoneNumber, String> {

    /** Un '+', un indicatif non nul, pour un total de 8 a 15 chiffres. */
    private static final Pattern E164 = Pattern.compile("^\\+[1-9][0-9]{7,14}$");

    /** Espaces et separateurs de saisie courants, retires a la normalisation. */
    private static final Pattern SEPARATORS = Pattern.compile("[\\s.()\\-]");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // La presence releve de @NotBlank : ne pas la dupliquer ici, sinon
        // un champ absent produit deux messages d'erreur distincts.
        return value == null || E164.matcher(value).matches();
    }

    /**
     * Normalise une saisie utilisateur avant validation et persistance.
     *
     * <p>Applique en amont de la validation dans les DTO d'entree, afin
     * que "+225 07 00 00 00 00" et "+2250700000000" designent bien le
     * meme compte.
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        return SEPARATORS.matcher(raw).replaceAll("");
    }
}
