package me.psikuvit.betterWarden.core.repo;

import me.psikuvit.betterWarden.core.model.PanelUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PanelUserRepository extends JpaRepository<PanelUser, Long> {

    Optional<PanelUser> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);
}
