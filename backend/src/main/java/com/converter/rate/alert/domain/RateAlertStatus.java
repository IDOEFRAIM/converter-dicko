package com.converter.rate.alert.domain;

/** Cycle de vie d'une {@link RateAlert} : chaque transition hors {@code ACTIVE} est finale. */
public enum RateAlertStatus {
    ACTIVE,
    TRIGGERED,
    CANCELLED,
    EXPIRED
}
