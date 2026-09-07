package com.converter.auth.dto;

import com.converter.user.domain.ExperienceProfile;
import jakarta.validation.constraints.NotNull;

public record UpdateExperienceProfileRequest(
        @NotNull(message = "Le profil d'experience est obligatoire")
        ExperienceProfile experienceProfile
) {
}
