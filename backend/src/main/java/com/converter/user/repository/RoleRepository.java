package com.converter.user.repository;

import com.converter.user.domain.Role;
import com.converter.user.domain.RoleCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Short> {

    Optional<Role> findByCode(RoleCode code);
}
