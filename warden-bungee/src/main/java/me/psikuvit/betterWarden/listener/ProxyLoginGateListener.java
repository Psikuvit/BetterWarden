package me.psikuvit.betterWarden.listener;

import me.psikuvit.betterWarden.core.config.CoreConfig;
import me.psikuvit.betterWarden.core.model.Punishment;
import me.psikuvit.betterWarden.core.service.IpHashingService;
import me.psikuvit.betterWarden.core.service.LangService;
import me.psikuvit.betterWarden.core.service.PunishmentGateway;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.bungeecord.BungeeComponentSerializer;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;
import org.slf4j.Logger;

import java.util.Optional;

/**
 * Network-wide ban gate - same PunishmentGateway seam as warden-velocity's own
 * ProxyLoginGateListener (works unchanged in HOST or CLIENT mode), ported to BungeeCord's
 * registerIntent()/completeIntent() async pattern instead of Velocity's EventTask.async().
 */
public class ProxyLoginGateListener implements Listener {

    private final Plugin plugin;
    private final ProxyServer server;
    private final PunishmentGateway punishmentService;
    private final IpHashingService ipHashing;
    private final CoreConfig config;
    private final LangService lang;
    private final Logger logger;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public ProxyLoginGateListener(Plugin plugin, ProxyServer server, PunishmentGateway punishmentService,
                                   IpHashingService ipHashing, CoreConfig config, LangService lang, Logger logger) {
        this.plugin = plugin;
        this.server = server;
        this.punishmentService = punishmentService;
        this.ipHashing = ipHashing;
        this.config = config;
        this.lang = lang;
        this.logger = logger;
    }

    @EventHandler
    public void onLogin(LoginEvent event) {
        event.registerIntent(plugin);
        server.getScheduler().runAsync(plugin, () -> {
            PendingConnection connection = event.getConnection();
            try {
                Optional<Punishment> ban = punishmentService.activeBan(connection.getUniqueId());
                if (ban.isEmpty()) {
                    String ip = connection.getAddress().getAddress().getHostAddress();
                    ban = punishmentService.activeIpBan(ipHashing.hash(ip));
                }
                ban.ifPresent(p -> {
                    String duration = p.isPermanent() ? lang.get("punish.permanent") : lang.get("punish.until", p.getExpiresAt());
                    String message = lang.get("gate.banned", p.getReason(), punishmentService.resolveStaffName(p), duration, p.getId());
                    event.setCancelled(true);
                    event.setCancelReason(BungeeComponentSerializer.get().serialize(miniMessage.deserialize(message)));
                });
            } catch (Exception e) {
                if (config.getLoginGate().isFailOpen()) {
                    logger.error("Login gate check failed for {} - allowing (fail-open)", connection.getName(), e);
                } else {
                    logger.error("Login gate check failed for {} - denying (fail-closed)", connection.getName(), e);
                    event.setCancelled(true);
                    event.setCancelReason(BungeeComponentSerializer.get().serialize(
                            miniMessage.deserialize("<red>Could not verify your ban status. Try again shortly.")));
                }
            } finally {
                event.completeIntent(plugin);
            }
        });
    }
}
