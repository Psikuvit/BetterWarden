package me.psikuvit.betterWarden.listener;

import me.psikuvit.betterWarden.core.model.Punishment;
import me.psikuvit.betterWarden.core.service.IpHashingService;
import me.psikuvit.betterWarden.core.service.LangService;
import me.psikuvit.betterWarden.core.service.PunishmentGateway;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.util.Optional;

/**
 * AsyncPlayerPreLoginEvent runs off-thread by design, so a blocking DB lookup here is fine.
 * Shared by both the Paper and plain-Spigot boot paths (see BetterWarden.java /
 * spigot/WardenSpigotPlugin.java) - uses disallow(Result, String) rather than the Component
 * overload since that's the one both platforms actually have (confirmed via javap: Spigot never
 * added the Component overload Paper did).
 */
public class BanGateListener implements Listener {

    private final PunishmentGateway service;
    private final IpHashingService ipHashing;
    private final LangService lang;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public BanGateListener(PunishmentGateway service, IpHashingService ipHashing, LangService lang) {
        this.service = service;
        this.ipHashing = ipHashing;
        this.lang = lang;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        Optional<Punishment> ban = service.activeBan(event.getUniqueId());
        if (ban.isEmpty()) {
            String ipHash = ipHashing.hash(event.getAddress().getHostAddress());
            ban = service.activeIpBan(ipHash);
        }
        if (ban.isEmpty()) {
            return;
        }
        Punishment p = ban.get();
        String duration = p.isPermanent() ? lang.get("punish.permanent") : lang.get("punish.until", p.getExpiresAt());
        String message = lang.get("gate.banned", p.getReason(), service.resolveStaffName(p), duration, p.getId());
        String legacy = LegacyComponentSerializer.legacySection().serialize(miniMessage.deserialize(message));
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, legacy);
    }
}
