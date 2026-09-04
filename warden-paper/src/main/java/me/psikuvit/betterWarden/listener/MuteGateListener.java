package me.psikuvit.betterWarden.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.psikuvit.betterWarden.core.service.LangService;
import me.psikuvit.betterWarden.core.service.PunishmentGateway;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class MuteGateListener implements Listener {

    private final PunishmentGateway service;
    private final LangService lang;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public MuteGateListener(PunishmentGateway service, LangService lang) {
        this.service = service;
        this.lang = lang;
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        service.activeMute(event.getPlayer().getUniqueId()).ifPresent(mute -> {
            event.setCancelled(true);
            event.getPlayer().sendMessage(miniMessage.deserialize(lang.get("gate.muted-chat", mute.getReason())));
        });
    }
}
