package com.converter.supplier.repository;

import com.converter.supplier.domain.Supplier;
import com.converter.supplier.domain.SupplierStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

    Page<Supplier> findByOwnerUserIdOrderByCreatedAtDesc(UUID ownerUserId, Pageable pageable);

    Page<Supplier> findByOwnerUserIdAndStatusOrderByCreatedAtDesc(UUID ownerUserId, SupplierStatus status,
                                                                   Pageable pageable);

    Page<Supplier> findByOwnerUserIdAndFavoriteTrueOrderByCreatedAtDesc(UUID ownerUserId, Pageable pageable);
}
