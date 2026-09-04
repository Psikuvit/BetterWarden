package me.psikuvit.betterWarden.command;

import me.psikuvit.betterWarden.core.repo.PlayerRepository;
import me.psikuvit.betterWarden.core.service.MojangApiService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Online, then warden's local player table (HOST mode), then an async Mojang lookup as a last
 * resort - same tiered approach as warden-paper's own TargetResolver, minus the middle
 * Bukkit-offline-cache tier: Server#getOfflinePlayerIfCached is a Paper-only addition, confirmed
 * absent from plain Spigot's API via javap, not assumed. Spigot's own Server#getOfflinePlayer(...)
 * exists but does a blocking lookup with very different (much worse) semantics, not a safe
 * synchronous substitute - skip straight to the local table instead of reaching for it.
 */
public final class TargetResolver {

    private TargetResolver() {
    }

    public record Target(UUID uuid, String name) {
    }

    /** Fast path only, never blocks: currently online. */
    public static Optional<Target> resolveCached(String name) {
        Player online = Bukkit.getPlayerExact(name);
        return online == null ? Optional.empty() : Optional.of(new Target(online.getUniqueId(), online.getName()));
    }

    /** HOST mode: adds warden's own player table before falling back to Mojang. */
    public static CompletableFuture<Optional<Target>> resolve(PlayerRepository players, MojangApiService mojangApi, String name) {
        Optional<Target> cached = resolveCached(name);
        if (cached.isPresent()) {
            return CompletableFuture.completedFuture(cached);
        }
        Optional<Target> local = players.findByLastNameIgnoreCase(name)
                .map(p -> new Target(UUID.fromString(p.getUuid()), p.getLastName()));
        if (local.isPresent()) {
            return CompletableFuture.completedFuture(local);
        }
        return viaMojang(mojangApi, name);
    }

    /** CLIENT mode: no local player table to check, straight to Mojang after the fast path. */
    public static CompletableFuture<Optional<Target>> resolve(MojangApiService mojangApi, String name) {
        Optional<Target> cached = resolveCached(name);
        if (cached.isPresent()) {
            return CompletableFuture.completedFuture(cached);
        }
        return viaMojang(mojangApi, name);
    }

    private static CompletableFuture<Optional<Target>> viaMojang(MojangApiService mojangApi, String name) {
        return mojangApi.lookupUuid(name).thenApply(uuid -> uuid.map(id -> new Target(id, name)));
    }
}
