package me.psikuvit.betterWarden.spigot.listener;

import me.psikuvit.betterWarden.core.service.LangService;
import me.psikuvit.betterWarden.core.service.PunishmentGateway;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/** AsyncChatEvent is Paper-only - plain Spigot's chat event is the older (deprecated but still functional) AsyncPlayerChatEvent. */
public class MuteGateListener implements Listener {

    private final PunishmentGateway service;
    private final LangService lang;
    private final BukkitAudiences audiences;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public MuteGateListener(PunishmentGateway service, LangService lang, BukkitAudiences audiences) {
        this.service = service;
        this.lang = lang;
        this.audiences = audiences;
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        service.activeMute(event.getPlayer().getUniqueId()).ifPresent(mute -> {
            event.setCancelled(true);
            audiences.player(event.getPlayer()).sendMessage(miniMessage.deserialize(lang.get("gate.muted-chat", mute.getReason())));
        });
    }
}
