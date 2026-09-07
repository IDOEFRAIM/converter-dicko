package com.converter.achievement.web;

import com.converter.achievement.dto.AchievementSummaryResponse;
import com.converter.achievement.service.AchievementService;
import com.converter.common.api.ApiResponse;
import com.converter.security.AuthenticatedUser;
import com.converter.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Mes gains" — jamais reserve a un sous-ensemble de comptes (contrairement au reporting
 * Business) : tout compte authentifie y a acces, memes gains nuls.
 */
@RestController
@RequestMapping("/api/v1/me/achievements")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Mes gains", description = "Volume transfere, badges et XP derives des ordres COMPLETED")
public class AchievementController {

    private final AchievementService achievementService;

    public AchievementController(AchievementService achievementService) {
        this.achievementService = achievementService;
    }

    @GetMapping
    @Operation(summary = "Resume de mes gains (volume, badge, XP)",
            description = "Purement derive des ordres deja COMPLETED -- jamais un recalcul de taux/frais. "
                    + "Aucun badge pour un profil PRO (mission : compteur d'activite sobre uniquement).")
    public ResponseEntity<ApiResponse<AchievementSummaryResponse>> summary(@AuthenticatedUser CurrentUser currentUser) {
        return ResponseEntity.ok(ApiResponse.of(achievementService.summary(currentUser.getId())));
    }
}
