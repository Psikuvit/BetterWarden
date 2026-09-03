package me.psikuvit.betterWarden.core.repo;

import me.psikuvit.betterWarden.core.model.PunishmentTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PunishmentTemplateRepository extends JpaRepository<PunishmentTemplate, Long> {

    Optional<PunishmentTemplate> findByKeyIgnoreCase(String key);
}
