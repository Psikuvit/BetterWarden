package me.psikuvit.betterWarden.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.psikuvit.betterWarden.core.service.ChatHistoryService;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/** Feeds ChatHistoryService for report snapshots. Runs at MONITOR and ignores cancellation - a muted player's blocked message still gets captured. */
public class ChatCaptureListener implements Listener {

    private final ChatHistoryService chatHistory;

    public ChatCaptureListener(ChatHistoryService chatHistory) {
        this.chatHistory = chatHistory;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChat(AsyncChatEvent event) {
        String text = PlainTextComponentSerializer.plainText().serialize(event.message());
        chatHistory.record(event.getPlayer().getUniqueId(), text);
    }
}
