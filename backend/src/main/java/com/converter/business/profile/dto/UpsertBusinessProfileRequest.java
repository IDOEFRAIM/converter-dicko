package com.converter.business.profile.dto;

import com.converter.business.profile.domain.BusinessType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Corps de {@code PUT /api/v1/business-profile} — creation si absent, mise a jour sinon (section
 * 7). Ne porte jamais {@code userId} : le proprietaire vient exclusivement de l'utilisateur
 * authentifie (section 8), jamais d'un champ du corps.
 */
public record UpsertBusinessProfileRequest(

        @NotBlank(message = "Le nom de l'entreprise est obligatoire")
        @Size(max = 160)
        String businessName,

        @NotNull(message = "Le type d'entreprise est obligatoire")
        BusinessType businessType,

        @Size(max = 60, message = "Le numero d'immatriculation depasse la longueur autorisee")
        String registrationNumber,

        @NotBlank(message = "Le pays est obligatoire")
        @Size(max = 100)
        String country,

        @Size(max = 100)
        String city,

        @Size(max = 255)
        String address
) {
}
