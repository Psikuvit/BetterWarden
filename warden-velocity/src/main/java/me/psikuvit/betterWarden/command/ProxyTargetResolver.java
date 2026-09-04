package me.psikuvit.betterWarden.command;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import me.psikuvit.betterWarden.core.repo.PlayerRepository;

import java.util.Optional;
import java.util.UUID;

/** Online players via the proxy directly; offline players via warden-core's own player table (no blocking network lookup). */
public final class ProxyTargetResolver {

    private ProxyTargetResolver() {
    }

    public record Target(UUID uuid, String name) {
    }

    public static Optional<Target> resolve(ProxyServer server, PlayerRepository players, String name) {
        Optional<Player> online = server.getPlayer(name);
        if (online.isPresent()) {
            Player p = online.get();
            return Optional.of(new Target(p.getUniqueId(), p.getUsername()));
        }
        return players.findByLastNameIgnoreCase(name)
                .map(p -> new Target(UUID.fromString(p.getUuid()), p.getLastName()));
    }
}
