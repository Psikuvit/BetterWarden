package me.psikuvit.betterWarden.listener;

import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.proxy.Player;
import me.psikuvit.betterWarden.core.config.CoreConfig;
import me.psikuvit.betterWarden.core.model.Punishment;
import me.psikuvit.betterWarden.core.service.IpHashingService;
import me.psikuvit.betterWarden.core.service.LangService;
import me.psikuvit.betterWarden.core.service.PunishmentGateway;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.slf4j.Logger;

import java.util.Optional;

/**
 * Network-wide ban gate. In HOST mode this reads straight from PunishmentCache - already
 * in-memory, warmed at boot, kept current by PunishmentChangedEvent (docs/spec: "resolved from
 * cache <1ms, updated by push never polled") - via PunishmentGateway, so the exact same listener
 * also works unchanged in CLIENT mode against RemotePunishmentCache. In HOST mode there's no
 * network I/O in this path, so the spec's "hard 250ms timeout" doesn't apply - the try/catch
 * below is what "fail-open by default" actually protects against there: an unexpected exception,
 * not slowness. In CLIENT mode the gateway itself IS a network call (REST-backed cache refresh),
 * so this try/catch is also the closest thing to that timeout right now - see PLAN.md.
 */
public class ProxyLoginGateListener {

    private final PunishmentGateway punishmentService;
    private final IpHashingService ipHashing;
    private final CoreConfig config;
    private final LangService lang;
    private final Logger logger;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public ProxyLoginGateListener(PunishmentGateway punishmentService, IpHashingService ipHashing,
                                   CoreConfig config, LangService lang, Logger logger) {
        this.punishmentService = punishmentService;
        this.ipHashing = ipHashing;
        this.config = config;
        this.lang = lang;
        this.logger = logger;
    }

    @Subscribe
    public EventTask onLogin(LoginEvent event) {
        return EventTask.async(() -> {
            Player player = event.getPlayer();
            try {
                Optional<Punishment> ban = punishmentService.activeBan(player.getUniqueId());
                if (ban.isEmpty()) {
                    String ip = player.getRemoteAddress().getAddress().getHostAddress();
                    ban = punishmentService.activeIpBan(ipHashing.hash(ip));
                }
                ban.ifPresent(p -> {
                    String duration = p.isPermanent() ? lang.get("punish.permanent") : lang.get("punish.until", p.getExpiresAt());
                    String message = lang.get("gate.banned", p.getReason(), punishmentService.resolveStaffName(p), duration, p.getId());
                    event.setResult(ResultedEvent.ComponentResult.denied(miniMessage.deserialize(message)));
                });
            } catch (Exception e) {
                if (config.getLoginGate().isFailOpen()) {
                    logger.error("Login gate check failed for {} - allowing (fail-open)", player.getUsername(), e);
                } else {
                    logger.error("Login gate check failed for {} - denying (fail-closed)", player.getUsername(), e);
                    event.setResult(ResultedEvent.ComponentResult.denied(
                            miniMessage.deserialize("<red>Could not verify your ban status. Try again shortly.")));
                }
            }
        });
    }
}
