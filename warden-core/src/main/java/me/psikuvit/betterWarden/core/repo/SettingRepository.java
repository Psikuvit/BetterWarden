package me.psikuvit.betterWarden.core.repo;

import me.psikuvit.betterWarden.core.model.Setting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettingRepository extends JpaRepository<Setting, String> {
}
