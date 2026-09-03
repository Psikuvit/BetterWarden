package me.psikuvit.betterWarden.listener;

import me.psikuvit.betterWarden.core.service.PlayerTrackingService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.net.InetSocketAddress;

public class PlayerTrackingListener implements Listener {

    private final PlayerTrackingService service;

    public PlayerTrackingListener(PlayerTrackingService service) {
        this.service = service;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        InetSocketAddress address = player.getAddress();
        String ip = address != null ? address.getAddress().getHostAddress() : null;
        service.trackJoinAsync(player.getUniqueId(), player.getName(), ip);
    }
}
