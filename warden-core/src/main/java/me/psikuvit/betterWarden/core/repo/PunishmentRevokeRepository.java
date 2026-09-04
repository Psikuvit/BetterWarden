package me.psikuvit.betterWarden.core.repo;

import me.psikuvit.betterWarden.core.model.PunishmentRevoke;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PunishmentRevokeRepository extends JpaRepository<PunishmentRevoke, Long> {
}
