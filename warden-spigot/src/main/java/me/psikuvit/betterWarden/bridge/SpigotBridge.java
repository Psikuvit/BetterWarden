package me.psikuvit.betterWarden.bridge;

import me.psikuvit.betterWarden.core.platform.PlatformBridge;
import me.psikuvit.betterWarden.core.platform.PlayerRef;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Same scheduler-hop need as PaperBridge (no Adventure-native Player, no Folia region threading
 * to worry about here though - plain Spigot only has the one legacy BukkitScheduler). Kick goes
 * through the legacy serializer, not BukkitAudiences - Player#kickPlayer(String) predates
 * Adventure entirely on this platform, there's no Component-accepting overload.
 */
public class SpigotBridge implements PlatformBridge {

    private final Plugin plugin;
    private final BukkitAudiences audiences;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public SpigotBridge(Plugin plugin, BukkitAudiences audiences) {
        this.plugin = plugin;
        this.audiences = audiences;
    }

    @Override
    public boolean isOnline(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        return player != null && player.isOnline();
    }

    @Override
    public Optional<PlayerRef> find(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        return player == null ? Optional.empty() : Optional.of(toRef(player));
    }

    @Override
    public Optional<PlayerRef> findByName(String name) {
        Player player = Bukkit.getPlayerExact(name);
        return player == null ? Optional.empty() : Optional.of(toRef(player));
    }

    @Override
    public void kick(UUID uuid, String reasonMiniMessage) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                String legacy = LegacyComponentSerializer.legacySection().serialize(miniMessage.deserialize(reasonMiniMessage));
                player.kickPlayer(legacy);
            }
        });
    }

    @Override
    public void message(UUID uuid, String messageMiniMessage) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                audiences.player(player).sendMessage(miniMessage.deserialize(messageMiniMessage));
            }
        });
    }

    @Override
    public void broadcastPermission(String permissionNode, String messageMiniMessage) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            var component = miniMessage.deserialize(messageMiniMessage);
            Bukkit.getOnlinePlayers().stream()
                    .filter(p -> p.hasPermission(permissionNode))
                    .forEach(p -> audiences.player(p).sendMessage(component));
        });
    }

    @Override
    public void runConsoleCommand(String command) {
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
    }

    @Override
    public String serverName() {
        return Bukkit.getServer().getName();
    }

    @Override
    public Set<PlayerRef> onlinePlayers() {
        return Bukkit.getOnlinePlayers().stream().map(this::toRef).collect(Collectors.toSet());
    }

    private PlayerRef toRef(Player player) {
        return new PlayerRef(player.getUniqueId(), player.getName(), serverName());
    }
}
