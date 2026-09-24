package com.converter.pool.domain;

/** Cycle de vie d'un {@link Pool} : ACTIVE -> SUCCEEDED | EXPIRED | CANCELLED, jamais l'inverse. */
public enum PoolStatus {
    ACTIVE,
    SUCCEEDED,
    EXPIRED,
    CANCELLED
}
