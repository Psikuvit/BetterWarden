package me.psikuvit.betterWarden.core.panel.auth;

import me.psikuvit.betterWarden.core.model.PanelRole;
import me.psikuvit.betterWarden.core.model.PanelUser;
import me.psikuvit.betterWarden.core.repo.PanelUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;

/**
 * No first-run wizard yet (spec §2 - setup code, Discord/Minecraft linking, etc. are all still
 * open). This is the minimum viable substitute: if no panel account exists at all, create one
 * owner account with a random one-time password and print it once. Never a hardcoded default -
 * that would be a real vulnerability the moment this ships.
 */
@Component
public class PanelBootstrap {

    private static final Logger log = LoggerFactory.getLogger(PanelBootstrap.class);
    private static final String DEFAULT_USERNAME = "admin";

    private final PanelUserRepository users;
    private final PasswordEncoder encoder;

    public PanelBootstrap(PanelUserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureOwnerAccount() {
        if (users.count() > 0) {
            return;
        }
        String password = HexFormat.of().formatHex(randomBytes(9));

        PanelUser owner = new PanelUser();
        owner.setUsername(DEFAULT_USERNAME);
        owner.setPasswordHash(encoder.encode(password));
        owner.setRole(PanelRole.OWNER);
        owner.setCreatedAt(Instant.now());
        owner.setMustChangePassword(true);
        users.save(owner);

        log.warn("=================================================================");
        log.warn(" No panel account existed - created one. Log in and change this");
        log.warn(" password immediately, this is only shown once:");
        log.warn(" Username: {}", DEFAULT_USERNAME);
        log.warn(" Password: {}", password);
        log.warn("=================================================================");
    }

    private static byte[] randomBytes(int count) {
        byte[] bytes = new byte[count];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }
}
