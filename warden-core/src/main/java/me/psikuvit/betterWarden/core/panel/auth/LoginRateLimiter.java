package me.psikuvit.betterWarden.core.panel.auth;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory login throttle (spec 04-PANEL.txt §3/§6: "failed-login rate limiting + lockout").
 * Two independent counters, both must clear: per-IP (stops one attacker scanning many
 * usernames) and per-username (stops a botnet targeting one account from many IPs). Resets on
 * a successful login. Not persisted - a restart clears it, which is fine for a login throttle.
 */
@Component
public class LoginRateLimiter {

    private static final int MAX_IP_ATTEMPTS = 10;
    private static final int MAX_USER_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final Duration LOCKOUT = Duration.ofMinutes(15);

    private record Bucket(int count, Instant windowStart, Instant lockedUntil) {
        static final Bucket EMPTY = new Bucket(0, Instant.EPOCH, Instant.EPOCH);
    }

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public boolean isBlocked(String ip, String username) {
        return remainingLockout("ip:" + ip).compareTo(Duration.ZERO) > 0
                || remainingLockout(userKey(username)).compareTo(Duration.ZERO) > 0;
    }

    /** Longest remaining lockout across both counters, for the response message. */
    public Duration remainingLockout(String ip, String username) {
        Duration ipRemaining = remainingLockout("ip:" + ip);
        Duration userRemaining = remainingLockout(userKey(username));
        return ipRemaining.compareTo(userRemaining) > 0 ? ipRemaining : userRemaining;
    }

    public void recordFailure(String ip, String username) {
        recordFailure("ip:" + ip, MAX_IP_ATTEMPTS);
        recordFailure(userKey(username), MAX_USER_ATTEMPTS);
    }

    public void recordSuccess(String ip, String username) {
        buckets.remove("ip:" + ip);
        buckets.remove(userKey(username));
    }

    private void recordFailure(String key, int maxAttempts) {
        Instant now = Instant.now();
        buckets.compute(key, (k, existing) -> {
            Bucket current = existing == null ? Bucket.EMPTY : existing;
            boolean windowExpired = Duration.between(current.windowStart(), now).compareTo(WINDOW) > 0;
            int count = (windowExpired ? 0 : current.count()) + 1;
            Instant windowStart = windowExpired ? now : current.windowStart();
            Instant lockedUntil = count >= maxAttempts ? now.plus(LOCKOUT) : current.lockedUntil();
            return new Bucket(count, windowStart, lockedUntil);
        });
    }

    private Duration remainingLockout(String key) {
        Bucket bucket = buckets.get(key);
        if (bucket == null) {
            return Duration.ZERO;
        }
        Duration remaining = Duration.between(Instant.now(), bucket.lockedUntil());
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    private static String userKey(String username) {
        return "user:" + username.toLowerCase(Locale.ROOT);
    }
}
