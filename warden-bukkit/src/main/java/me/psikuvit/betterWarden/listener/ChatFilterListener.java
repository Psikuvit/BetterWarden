package me.psikuvit.betterWarden.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.psikuvit.betterWarden.command.Msg;
import me.psikuvit.betterWarden.core.model.FilterAction;
import me.psikuvit.betterWarden.core.platform.PlatformBridge;
import me.psikuvit.betterWarden.core.service.ChatFilterService;
import me.psikuvit.betterWarden.core.service.LangService;
import me.psikuvit.betterWarden.scheduler.WardenScheduler;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Runs before ChatCaptureListener's MONITOR pass, so a blocked message is still captured for
 * report snapshots (that listener ignores cancellation on purpose). setCancelled() is safe to
 * call directly from this async event per Paper's own contract; sendMessage isn't guaranteed
 * safe off-thread (same reasoning as command Async.runOnMain), so that part hops to main.
 */
public class ChatFilterListener implements Listener {

    private final ChatFilterService filterService;
    private final LangService lang;
    private final WardenScheduler scheduler;
    private final PlatformBridge bridge;

    public ChatFilterListener(ChatFilterService filterService, LangService lang, WardenScheduler scheduler, PlatformBridge bridge) {
        this.filterService = filterService;
        this.lang = lang;
        this.scheduler = scheduler;
        this.bridge = bridge;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String text = PlainTextComponentSerializer.plainText().serialize(event.message());

        ChatFilterService.FilterResult result = filterService.apply(player.getUniqueId(), player.getName(), bridge.serverName(), text);
        if (result.action() == FilterAction.BLOCKED) {
            event.setCancelled(true);
            scheduler.runGlobal(() -> Msg.send(player, lang.get("chat-filter.blocked")));
        }
        // FLAGGED: message goes through unmodified - already queued for review by apply().
    }
}
