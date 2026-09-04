package me.psikuvit.betterWarden.command;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

/** Resolves online players directly; offline players only if already cached locally (no blocking network lookup). */
public final class TargetResolver {

    private TargetResolver() {
    }

    public record Target(UUID uuid, String name) {
    }

    public static Optional<Target> resolve(String name) {
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
}
