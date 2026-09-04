package me.psikuvit.betterWarden.core.panel.setup;

import me.psikuvit.betterWarden.core.config.CoreConfig;
import me.psikuvit.betterWarden.core.repo.PanelUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * docs/spec/04-PANEL.txt §2, MVP steps 1-2 only (enter setup code, create owner account) -
 * Minecraft-account linking, Discord bot token, LiteBans import preview, and Cloudflare Tunnel
 * (steps 3-6) are still open, see PLAN.md. "/warden setup" reissuing the code in-game isn't
 * wired up either - this only prints it to the console on boot.
 */
@Component
public class SetupBootstrap {

    private static final Logger log = LoggerFactory.getLogger(SetupBootstrap.class);

    private final PanelUserRepository users;
    private final SetupCodeService setupCode;
    private final CoreConfig config;

    public SetupBootstrap(PanelUserRepository users, SetupCodeService setupCode, CoreConfig config) {
        this.users = users;
        this.setupCode = setupCode;
        this.config = config;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void announceIfNeeded() {
        if (users.count() > 0) {
            return;
        }
        String code = setupCode.generate();
        int port = config.getPanel().getPort();

        log.warn("=================================================================");
        log.warn(" Panel:      http://localhost:{}  (replace localhost with this", port);
        log.warn("             machine's address if accessing it remotely)");
        log.warn(" Setup code: {}  (30 min, single use)", code);
        log.warn(" Open the panel and enter this code to create your owner account.");
        log.warn("=================================================================");
    }
}
