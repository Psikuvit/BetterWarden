package me.psikuvit.betterWarden.core.platform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Fallback bridge used until a real platform bridge is registered. */
public class NoOpBridge implements PlatformBridge {

    private static final Logger log = LoggerFactory.getLogger(NoOpBridge.class);

    @Override
    public boolean isOnline(UUID uuid) {
        return false;
    }

    @Override
    public Optional<PlayerRef> find(UUID uuid) {
        return Optional.empty();
    }

    @Override
    public Optional<PlayerRef> findByName(String name) {
        return Optional.empty();
    }

    @Override
    public void kick(UUID uuid, String reasonMiniMessage) {
        log.warn("NoOpBridge: would kick {} ({})", uuid, reasonMiniMessage);
    }

    @Override
    public void message(UUID uuid, String messageMiniMessage) {
        log.info("NoOpBridge: would message {}: {}", uuid, messageMiniMessage);
    }

    @Override
    public void broadcastPermission(String permissionNode, String messageMiniMessage) {
        log.info("NoOpBridge: would broadcast to '{}': {}", permissionNode, messageMiniMessage);
    }

    @Override
    public void runConsoleCommand(String command) {
        log.warn("NoOpBridge: would run console command: {}", command);
    }

    @Override
    public String serverName() {
        return "noop";
    }

    @Override
    public Set<PlayerRef> onlinePlayers() {
        return Set.of();
    }
}
