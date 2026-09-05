package me.psikuvit.betterWarden.core.panel.setup;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;

/** In-memory only, single code at a time - matches "single use, 30 min" from docs/spec/04-PANEL.txt §2. Lost on restart, which is fine: SetupBootstrap regenerates one on every boot while setup is still incomplete. */
@Component
public class SetupCodeService {

    private static final Duration TTL = Duration.ofMinutes(30);
    private static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"; // no 0/O/1/I - avoids misreads
    private static final SecureRandom RANDOM = new SecureRandom();

    private volatile String code;
    private volatile Instant expiresAt;

    public synchronized String generate() {
        code = randomGroup() + "-" + randomGroup();
        expiresAt = Instant.now().plus(TTL);
        return code;
    }

    public synchronized boolean verify(String presented) {
        return expiresAt != null
                && presented != null && presented.equalsIgnoreCase(code)
                && Instant.now().isBefore(expiresAt);
    }

    public synchronized void invalidate() {
        code = null;
        expiresAt = null;
    }

    private static String randomGroup() {
        StringBuilder sb = new StringBuilder(4);
        for (int i = 0; i < 4; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
