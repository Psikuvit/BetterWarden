package me.psikuvit.betterWarden.core.api;

import me.psikuvit.betterWarden.core.repo.PlayerRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class HealthController {

    private final PlayerRepository playerRepository;

    public HealthController(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "ok",
                "time", Instant.now().toString(),
                "players", playerRepository.count()
        );
    }
}
