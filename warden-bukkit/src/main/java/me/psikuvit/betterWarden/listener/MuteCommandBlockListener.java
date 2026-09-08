package me.psikuvit.betterWarden.listener;

import me.psikuvit.betterWarden.core.service.LangService;
import me.psikuvit.betterWarden.core.service.PunishmentGateway;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Locale;
import java.util.Set;

/** Blocks a small set of chat-adjacent commands while muted (/msg, /r, /me, ...). */
public class MuteCommandBlockListener implements Listener {

    private static final Set<String> BLOCKED = Set.of("msg", "message", "tell", "w", "r", "reply", "me");

    private final PunishmentGateway service;
    private final LangService lang;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public MuteCommandBlockListener(PunishmentGateway service, LangService lang) {
        this.service = service;
        this.lang = lang;
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String label = event.getMessage().substring(1).split(" ", 2)[0]
                .split(":", 2)[0]
                .toLowerCase(Locale.ROOT);
        if (!BLOCKED.contains(label)) {
            return;
        }
        service.activeMute(event.getPlayer().getUniqueId()).ifPresent(mute -> {
            event.setCancelled(true);
            event.getPlayer().sendMessage(miniMessage.deserialize(lang.get("gate.muted-command", mute.getReason())));
        });
    }
}
