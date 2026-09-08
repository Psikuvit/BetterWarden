package me.psikuvit.betterWarden.command;

import me.psikuvit.betterWarden.core.repo.PlayerRepository;
import me.psikuvit.betterWarden.core.service.MojangApiService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Resolves a player name to a UUID, cheapest source first: online, then Bukkit's own offline
 * cache, then (HOST mode only) warden's local player table, then - only if nothing local knows
 * the name - an async Mojang lookup. Only the last tier ever touches the network, so callers
 * that can't afford to block (Brigadier's .executes() runs synchronously) must use resolve(),
 * never call resolveCached() and assume it's enough for a real "not found" answer.
 */
public final class TargetResolver {

    private TargetResolver() {
    }

    public record Target(UUID uuid, String name) {
    }

    /** Fast path only, never blocks: online or already Bukkit-cached. */
    public static Optional<Target> resolveCached(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return Optional.of(new Target(online.getUniqueId(), online.getName()));
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached != null) {
            String resolvedName = cached.getName() != null ? cached.getName() : name;
            return Optional.of(new Target(cached.getUniqueId(), resolvedName));
        }
        return Optional.empty();
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
