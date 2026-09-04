package me.psikuvit.betterWarden.bridge;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import me.psikuvit.betterWarden.core.platform.PlatformBridge;
import me.psikuvit.betterWarden.core.platform.PlayerRef;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * No scheduler hop needed here, unlike PaperBridge - Velocity's ProxyServer/Player
 * calls are safe from any thread, there's no "main thread" restriction.
 */
public class VelocityBridge implements PlatformBridge {

    private final ProxyServer server;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public VelocityBridge(ProxyServer server) {
        this.server = server;
    }

    @Override
    public boolean isOnline(UUID uuid) {
        return server.getPlayer(uuid).isPresent();
    }

    @Override
    public Optional<PlayerRef> find(UUID uuid) {
        return server.getPlayer(uuid).map(this::toRef);
    }

    @Override
    public Optional<PlayerRef> findByName(String name) {
        return server.getPlayer(name).map(this::toRef);
    }

    @Override
    public void kick(UUID uuid, String reasonMiniMessage) {
        server.getPlayer(uuid).ifPresent(p -> p.disconnect(miniMessage.deserialize(reasonMiniMessage)));
    }

    @Override
    public void message(UUID uuid, String messageMiniMessage) {
        server.getPlayer(uuid).ifPresent(p -> p.sendMessage(miniMessage.deserialize(messageMiniMessage)));
    }

    @Override
    public void broadcastPermission(String permissionNode, String messageMiniMessage) {
        var component = miniMessage.deserialize(messageMiniMessage);
        server.getAllPlayers().stream()
                .filter(p -> p.hasPermission(permissionNode))
                .forEach(p -> p.sendMessage(component));
    }

    @Override
    public void runConsoleCommand(String command) {
        server.getCommandManager().executeAsync(server.getConsoleCommandSource(), command);
    }

    @Override
    public String serverName() {
        // Node identity for the proxy itself - refined once NodeRegistry (Stage 3) exists.
        return "velocity-proxy";
    }

    @Override
    public Set<PlayerRef> onlinePlayers() {
        return server.getAllPlayers().stream().map(this::toRef).collect(Collectors.toSet());
    }

    private PlayerRef toRef(Player player) {
        String backend = player.getCurrentServer().map(sc -> sc.getServerInfo().getName()).orElse("unknown");
        return new PlayerRef(player.getUniqueId(), player.getUsername(), backend);
    }
}
