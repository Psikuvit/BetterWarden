package me.psikuvit.betterWarden.core.repo;

import me.psikuvit.betterWarden.core.model.DiscordLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DiscordLinkRepository extends JpaRepository<DiscordLink, String> {

    Optional<DiscordLink> findByMinecraftUuid(String minecraftUuid);
}
