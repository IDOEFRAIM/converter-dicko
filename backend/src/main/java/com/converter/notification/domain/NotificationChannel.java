package com.converter.notification.domain;

/**
 * Canal de diffusion d'une notification. MVP : {@code IN_APP} uniquement
 * -- aucun SMS/WhatsApp/email/push externe n'est integre. Le champ
 * existe pour preparer le modele a de futurs canaux sans migration
 * supplementaire lors de leur ajout.
 */
public enum NotificationChannel {
    IN_APP
}
