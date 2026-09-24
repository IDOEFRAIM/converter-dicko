package com.converter.business.reporting.dto;

import java.time.Instant;

/** {@code null} = borne non fournie (periode ouverte de ce cote). */
public record ReportPeriod(Instant from, Instant to) {
}
