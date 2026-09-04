package me.psikuvit.betterWarden.command;

import me.psikuvit.betterWarden.core.repo.PlayerRepository;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.util.Optional;
import java.util.UUID;

/** Online players via the proxy directly; offline players via warden-core's own player table (no blocking network lookup) - same approach as warden-velocity's own ProxyTargetResolver. */
public final class ProxyTargetResolver {

    private ProxyTargetResolver() {
    }

    public record Target(UUID uuid, String name) {
    }

    public static Optional<Target> resolve(ProxyServer server, PlayerRepository players, String name) {
        ProxiedPlayer online = server.getPlayer(name);
        if (online != null) {
            return Optional.of(new Target(online.getUniqueId(), online.getName()));
        }
        return players.findByLastNameIgnoreCase(name)
                .map(p -> new Target(UUID.fromString(p.getUuid()), p.getLastName()));
    }
}
