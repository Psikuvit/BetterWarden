package me.psikuvit.betterWarden.bridge;

import me.psikuvit.betterWarden.core.platform.PlatformBridge;
import me.psikuvit.betterWarden.core.platform.PlayerRef;
import net.kyori.adventure.platform.bungeecord.BungeeAudiences;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.bungeecord.BungeeComponentSerializer;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * No scheduler hop needed, same as VelocityBridge - Bungee's ProxyServer/ProxiedPlayer calls are
 * safe from any thread. Audience (from BungeeAudiences) has no kick/disconnect concept - that's
 * not a chat feature - so kick() goes through BungeeComponentSerializer to get a BaseComponent[]
 * ProxiedPlayer#disconnect() can actually take.
 */
public class BungeeBridge implements PlatformBridge {

    private final ProxyServer server;
    private final BungeeAudiences audiences;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public BungeeBridge(ProxyServer server, BungeeAudiences audiences) {
        this.server = server;
        this.audiences = audiences;
    }

    @Override
    public boolean isOnline(UUID uuid) {
        return server.getPlayer(uuid) != null;
    }

    @Override
    public Optional<PlayerRef> find(UUID uuid) {
        return Optional.ofNullable(server.getPlayer(uuid)).map(this::toRef);
    }

    @Override
    public Optional<PlayerRef> findByName(String name) {
        return Optional.ofNullable(server.getPlayer(name)).map(this::toRef);
    }

    @Override
    public void kick(UUID uuid, String reasonMiniMessage) {
        ProxiedPlayer player = server.getPlayer(uuid);
        if (player != null) {
            player.disconnect(BungeeComponentSerializer.get().serialize(miniMessage.deserialize(reasonMiniMessage)));
        }
    }

    @Override
    public void message(UUID uuid, String messageMiniMessage) {
        ProxiedPlayer player = server.getPlayer(uuid);
        if (player != null) {
            audiences.player(player).sendMessage(miniMessage.deserialize(messageMiniMessage));
        }
    }

    @Override
    public void broadcastPermission(String permissionNode, String messageMiniMessage) {
        var component = miniMessage.deserialize(messageMiniMessage);
        server.getPlayers().stream()
                .filter(p -> p.hasPermission(permissionNode))
                .forEach(p -> audiences.player(p).sendMessage(component));
    }

    @Override
    public void runConsoleCommand(String command) {
        server.getPluginManager().dispatchCommand(server.getConsole(), command);
    }

    @Override
    public String serverName() {
        // Node identity for the proxy itself - refined once NodeRegistry tracks per-node identity, same open item as VelocityBridge.
        return "bungee-proxy";
    }

    @Override
    public Set<PlayerRef> onlinePlayers() {
        return server.getPlayers().stream().map(this::toRef).collect(Collectors.toSet());
    }

    private PlayerRef toRef(ProxiedPlayer player) {
        Server backend = player.getServer();
        String backendName = backend == null ? "unknown" : backend.getInfo().getName();
        return new PlayerRef(player.getUniqueId(), player.getName(), backendName);
    }
}
