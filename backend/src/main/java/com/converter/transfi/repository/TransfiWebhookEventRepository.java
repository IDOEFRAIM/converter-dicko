package com.converter.transfi.repository;

import com.converter.transfi.domain.TransfiWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TransfiWebhookEventRepository extends JpaRepository<TransfiWebhookEvent, UUID> {

    boolean existsByProviderEventId(String providerEventId);
}
