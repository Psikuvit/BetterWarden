package me.psikuvit.betterWarden.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.psikuvit.betterWarden.core.service.ChatInputService;
import me.psikuvit.betterWarden.scheduler.WardenScheduler;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.UUID;

/** Intercepts chat for players with a pending GUI text-input request - see ChatInputService. */
public class ChatInputListener implements Listener {

    private final ChatInputService inputService;
    private final WardenScheduler scheduler;

    public ChatInputListener(ChatInputService inputService, WardenScheduler scheduler) {
        this.inputService = inputService;
        this.scheduler = scheduler;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (!inputService.hasPending(uuid)) {
            return;
        }
        event.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(event.message());
        // The callback may open inventories / touch Bukkit API - hop back to the main thread.
        scheduler.runGlobal(() -> inputService.submit(uuid, text));
    }
}
