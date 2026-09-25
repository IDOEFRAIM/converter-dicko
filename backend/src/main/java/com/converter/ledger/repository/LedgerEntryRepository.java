package com.converter.ledger.repository;

import com.converter.ledger.domain.LedgerEntry;
import com.converter.ledger.domain.LedgerEntryType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    boolean existsByOrderIdAndEntryType(UUID orderId, LedgerEntryType entryType);

    Page<LedgerEntry> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<LedgerEntry> findAllByOrderIdOrderByCreatedAtDesc(UUID orderId, Pageable pageable);
}
