package me.psikuvit.betterWarden.core.discord;

import me.psikuvit.betterWarden.core.platform.PlatformBridge;
import me.psikuvit.betterWarden.core.repo.PlayerRepository;
import me.psikuvit.betterWarden.core.service.MojangApiService;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Same tiered resolution as every platform's own TargetResolver (online -> local player table ->
 * Mojang), but Core has no Bukkit/Velocity of its own to ask "is this player online" - PlatformBridge
 * already exists for exactly this (every HOST-mode boot registers one, see WardenSpringApp).
 */
public final class DiscordTargetResolver {

    private DiscordTargetResolver() {
    }

    public record Target(UUID uuid, String name) {
    }

    public static CompletableFuture<Optional<Target>> resolve(PlatformBridge bridge, PlayerRepository players,
                                                                MojangApiService mojangApi, String name) {
        Optional<Target> online = bridge.findByName(name).map(ref -> new Target(ref.uuid(), ref.name()));
        if (online.isPresent()) {
            return CompletableFuture.completedFuture(online);
        }
        Optional<Target> local = players.findByLastNameIgnoreCase(name)
                .map(p -> new Target(UUID.fromString(p.getUuid()), p.getLastName()));
        if (local.isPresent()) {
            return CompletableFuture.completedFuture(local);
        }
        return mojangApi.lookupUuid(name).thenApply(uuid -> uuid.map(id -> new Target(id, name)));
    }
}
