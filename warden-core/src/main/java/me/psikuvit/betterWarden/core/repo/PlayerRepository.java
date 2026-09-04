package me.psikuvit.betterWarden.core.repo;

import me.psikuvit.betterWarden.core.model.Player;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlayerRepository extends JpaRepository<Player, String> {

    Optional<Player> findByLastNameIgnoreCase(String lastName);
}
