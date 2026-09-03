package me.psikuvit.betterWarden.core.platform;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Seam that keeps core free of Bukkit/Velocity imports (docs/spec/01-CORE.txt §2). */
public interface PlatformBridge {

    boolean isOnline(UUID uuid);

    Optional<PlayerRef> find(UUID uuid);

    Optional<PlayerRef> findByName(String name);

    void kick(UUID uuid, String reasonMiniMessage);

    void message(UUID uuid, String messageMiniMessage);

    void broadcastPermission(String permissionNode, String messageMiniMessage);

    void runConsoleCommand(String command);

    String serverName();

    Set<PlayerRef> onlinePlayers();
}
