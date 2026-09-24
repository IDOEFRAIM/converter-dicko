package com.converter.settings.repository;

import com.converter.settings.domain.SystemSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SystemSettingRepository extends JpaRepository<SystemSetting, String> {

    List<SystemSetting> findAllByPublicSettingTrue();

    List<SystemSetting> findAllByOrderBySettingKeyAsc();
}
