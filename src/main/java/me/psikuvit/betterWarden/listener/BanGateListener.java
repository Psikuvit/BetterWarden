package me.psikuvit.betterWarden.listener;

import me.psikuvit.betterWarden.core.model.Punishment;
import me.psikuvit.betterWarden.core.service.IpHashingService;
import me.psikuvit.betterWarden.core.service.PunishmentService;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.util.Optional;

/** AsyncPlayerPreLoginEvent runs off-thread by design, so a blocking DB lookup here is fine. */
public class BanGateListener implements Listener {

    private final PunishmentService service;
    private final IpHashingService ipHashing;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public BanGateListener(PunishmentService service, IpHashingService ipHashing) {
        this.service = service;
        this.ipHashing = ipHashing;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        Optional<Punishment> ban = service.activeBan(event.getUniqueId());
        if (ban.isEmpty() && event.getAddress() != null) {
            String ipHash = ipHashing.hash(event.getAddress().getHostAddress());
            ban = service.activeIpBan(ipHash);
        }
        if (ban.isEmpty()) {
            return;
        }
        Punishment p = ban.get();
        String duration = p.isPermanent() ? "Permanent" : "Until " + p.getExpiresAt();
        String message = "<red>You are banned.\n<gray>Reason: " + p.getReason() + "\n<gray>Duration: " + duration;
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, miniMessage.deserialize(message));
    }
}
