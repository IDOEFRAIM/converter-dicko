package com.converter.rate.cost.repository;

import com.converter.rate.cost.domain.DailyCostRateConfiguration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DailyCostRateConfigurationRepository extends JpaRepository<DailyCostRateConfiguration, UUID> {

    Optional<DailyCostRateConfiguration> findFirstByOrderByCreatedAtDesc();

    Page<DailyCostRateConfiguration> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
