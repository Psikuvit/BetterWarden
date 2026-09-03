package me.psikuvit.betterWarden.core.platform;

import java.util.UUID;

/**
 * A platform-neutral reference to an online player. Deliberately not
 * Bukkit's {@code Player} or Velocity's {@code Player} — core never sees
 * either.
 */
public record PlayerRef(UUID uuid, String name, String server) {
}
