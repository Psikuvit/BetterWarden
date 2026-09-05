package me.psikuvit.betterWarden.core.repo;

import me.psikuvit.betterWarden.core.model.Player;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlayerRepository extends JpaRepository<Player, String> {

    Optional<Player> findByLastNameIgnoreCase(String lastName);

    /** Panel's /players search (spec §1) - name substring only; a bare uuid query is handled separately by the controller via findById. */
    List<Player> findByLastNameContainingIgnoreCaseOrderByLastSeenDesc(String query, Pageable pageable);
}
