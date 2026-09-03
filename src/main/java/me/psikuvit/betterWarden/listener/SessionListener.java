package me.psikuvit.betterWarden.listener;

import me.psikuvit.betterWarden.core.service.PlayerTrackingService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class SessionListener implements Listener {

    private final PlayerTrackingService service;

    public SessionListener(PlayerTrackingService service) {
        this.service = service;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        service.flushSessionAsync(event.getPlayer().getUniqueId());
    }
}
