import 'dart:io';

import 'package:flutter/material.dart';

import '../../core/theme/app_colors.dart';
import '../../core/theme/app_spacing.dart';
import '../../core/theme/app_typography.dart';

/// « Filtre » du livre memoire (remarque produit #5) : le selfie souvenir d'un
/// transfert, encadre au moment de l'**affichage** (jamais composite dans le
/// fichier — l'image reste brute sur disque). Double filet d'or + bandeau
/// grave (date · montant) : la mise en scene vient du cadre, pas d'un effet
/// applique au pixel.
class MemoryFrame extends StatelessWidget {
  final String imagePath;
  final String dateLabel;
  final String amountLabel;
  final double? width;
  final double? height;
  final bool compact;

  const MemoryFrame({
    super.key,
    required this.imagePath,
    required this.dateLabel,
    required this.amountLabel,
    this.width,
    this.height,
    this.compact = false,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      width: width,
      height: height,
      padding: const EdgeInsets.all(3),
      decoration: BoxDecoration(
        color: AppColors.lacquer,
        borderRadius: BorderRadius.circular(AppSpacing.radiusSm + 3),
        border: Border.all(color: AppColors.keyline.withValues(alpha: 0.7), width: 1.5),
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(AppSpacing.radiusSm),
        child: Stack(
          fit: StackFit.expand,
          children: [
            Image.file(
              File(imagePath),
              fit: BoxFit.cover,
              cacheWidth: compact ? 240 : 900,
              errorBuilder: (_, _, _) => const ColoredBox(
                color: AppColors.lacquerEdge,
                child: Center(
                  child: Icon(Icons.image_not_supported_outlined, color: AppColors.onLacquerMuted, size: 24),
                ),
              ),
            ),
            // Filet d'or interne (le "cadre gravé").
            Positioned.fill(
              child: DecoratedBox(
                decoration: BoxDecoration(
                  border: Border.all(color: AppColors.keyline.withValues(alpha: 0.45)),
                ),
              ),
            ),
            // Bandeau gravé : date · montant.
            Positioned(
              left: 0,
              right: 0,
              bottom: 0,
              child: Container(
                padding: EdgeInsets.symmetric(
                  horizontal: compact ? AppSpacing.xs : AppSpacing.sm,
                  vertical: compact ? 3 : AppSpacing.xs,
                ),
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    begin: Alignment.topCenter,
                    end: Alignment.bottomCenter,
                    colors: [
                      AppColors.lacquer.withValues(alpha: 0),
                      AppColors.lacquer.withValues(alpha: 0.85),
                    ],
                  ),
                ),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Flexible(
                      child: Text(
                        dateLabel,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: (compact ? AppTypography.caption : AppTypography.figureSmall)
                            .copyWith(color: AppColors.onLacquer),
                      ),
                    ),
                    const SizedBox(width: AppSpacing.xs),
                    Flexible(
                      child: Text(
                        amountLabel,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        textAlign: TextAlign.right,
                        style: (compact ? AppTypography.caption : AppTypography.figureSmall)
                            .copyWith(color: AppColors.keyline, fontWeight: FontWeight.w700),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
