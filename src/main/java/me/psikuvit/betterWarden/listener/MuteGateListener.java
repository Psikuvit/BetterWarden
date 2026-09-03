package me.psikuvit.betterWarden.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.psikuvit.betterWarden.core.service.PunishmentService;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class MuteGateListener implements Listener {

    private final PunishmentService service;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public MuteGateListener(PunishmentService service) {
        this.service = service;
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        service.activeMute(event.getPlayer().getUniqueId()).ifPresent(mute -> {
            event.setCancelled(true);
            event.getPlayer().sendMessage(miniMessage.deserialize("<red>You are muted: <gray>" + mute.getReason()));
        });
    }
}
