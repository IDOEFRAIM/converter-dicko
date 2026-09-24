package com.converter.settings.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Parametre metier administrable a chaud.
 *
 * <p>La cle primaire est la cle du parametre elle-meme : elle est
 * stable, lisible dans les journaux, et rend impossible l'existence de
 * deux lignes concurrentes pour un meme reglage.
 */
@Entity
@Table(name = "system_settings")
@EntityListeners(AuditingEntityListener.class)
public class SystemSetting {

    @Id
    @Column(name = "setting_key", nullable = false, length = 64)
    private String settingKey;

    @Column(name = "value", nullable = false, length = 255)
    private String value;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false, length = 16)
    private SettingType valueType;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "is_public", nullable = false)
    private boolean publicSetting;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected SystemSetting() {
        // Requis par JPA.
    }

    public void updateValue(String newValue, UUID actorId) {
        this.value = newValue;
        this.updatedBy = actorId;
    }

    public SettingKey key() {
        return SettingKey.valueOf(settingKey);
    }

    public String getSettingKey() {
        return settingKey;
    }

    public String getValue() {
        return value;
    }

    public SettingType getValueType() {
        return valueType;
    }

    public String getDescription() {
        return description;
    }

    public boolean isPublicSetting() {
        return publicSetting;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof SystemSetting setting
                && Objects.equals(settingKey, setting.settingKey);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(settingKey);
    }
}
