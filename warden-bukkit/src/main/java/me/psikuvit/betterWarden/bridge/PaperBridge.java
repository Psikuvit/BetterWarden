package me.psikuvit.betterWarden.bridge;

import me.psikuvit.betterWarden.core.platform.PlatformBridge;
import me.psikuvit.betterWarden.core.platform.PlayerRef;
import me.psikuvit.betterWarden.scheduler.WardenScheduler;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class PaperBridge implements PlatformBridge {

    private final WardenScheduler scheduler;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public PaperBridge(WardenScheduler scheduler) {
        this.scheduler = scheduler;
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
        scheduler.runGlobal(() -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.kick(miniMessage.deserialize(reasonMiniMessage));
            }
        });
    }

    @Override
    public void message(UUID uuid, String messageMiniMessage) {
        scheduler.runGlobal(() -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendMessage(miniMessage.deserialize(messageMiniMessage));
            }
        });
    }

    @Override
    public void broadcastPermission(String permissionNode, String messageMiniMessage) {
        scheduler.runGlobal(() -> Bukkit.getServer().broadcast(miniMessage.deserialize(messageMiniMessage), permissionNode));
    }

    @Override
    public void runConsoleCommand(String command) {
        scheduler.runGlobal(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
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
