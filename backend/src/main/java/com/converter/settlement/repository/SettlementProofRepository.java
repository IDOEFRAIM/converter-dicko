package com.converter.settlement.repository;

import com.converter.settlement.domain.SettlementProof;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SettlementProofRepository extends JpaRepository<SettlementProof, UUID> {

    List<SettlementProof> findBySettlementIdOrderByUploadedAtAsc(UUID settlementId);

    long countBySettlementId(UUID settlementId);
}
