package me.psikuvit.betterWarden.core.discord;

import me.psikuvit.betterWarden.core.model.DiscordLink;
import me.psikuvit.betterWarden.core.repo.DiscordLinkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * docs/spec/05-DISCORD-BOT.txt §6 account linking. `/link` in Discord generates a short code
 * (pending, in-memory - same "lost on restart is fine" reasoning as SetupCodeService, a player
 * just re-runs /link); `/link <code>` in-game redeems it into a persistent DiscordLink row.
 * Multiple codes can be pending at once (unlike SetupCodeService's single global code), one per
 * Discord user trying to link concurrently.
 */
@Service
public class DiscordLinkService {

    private static final Duration TTL = Duration.ofMinutes(10);
    private static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"; // no 0/O/1/I - avoids misreads
    private static final SecureRandom RANDOM = new SecureRandom();

    private record Pending(String discordUserId, String discordTag, Instant expiresAt) {
    }

    private final Map<String, Pending> pending = new ConcurrentHashMap<>();
    private final DiscordLinkRepository links;

    public DiscordLinkService(DiscordLinkRepository links) {
        this.links = links;
    }

    /** Invalidates any code this Discord user already had pending before minting a new one. */
    public String generateCode(String discordUserId, String discordTag) {
        purgeExpired();
        pending.values().removeIf(p -> p.discordUserId().equals(discordUserId));
        String code = randomCode();
        pending.put(code, new Pending(discordUserId, discordTag, Instant.now().plus(TTL)));
        return code;
    }

    /** Null if this Discord user has no code currently pending (never generated one, or it expired). */
    public String pendingTagFor(String discordUserId) {
        purgeExpired();
        return pending.values().stream()
                .filter(p -> p.discordUserId().equals(discordUserId))
                .findFirst().map(Pending::discordTag).orElse(null);
    }

    /**
     * Redeems a code typed in-game. Fails if the code is unknown/expired, or if either side is
     * already linked to something else - unlink first rather than silently repointing a link.
     */
    @Transactional
    public RedeemResult redeem(String code, UUID minecraftUuid) {
        purgeExpired();
        Pending p = code == null ? null : pending.get(code.toUpperCase(java.util.Locale.ROOT));
        if (p == null) {
            return RedeemResult.invalidCode();
        }
        if (links.existsById(p.discordUserId())) {
            pending.remove(code.toUpperCase(java.util.Locale.ROOT));
            return RedeemResult.alreadyLinkedDiscord();
        }
        if (links.findByMinecraftUuid(minecraftUuid.toString()).isPresent()) {
            return RedeemResult.alreadyLinkedMinecraft();
        }
        DiscordLink link = new DiscordLink();
        link.setDiscordUserId(p.discordUserId());
        link.setMinecraftUuid(minecraftUuid.toString());
        link.setLinkedAt(Instant.now());
        links.save(link);
        pending.remove(code.toUpperCase(java.util.Locale.ROOT));
        return RedeemResult.success(p.discordTag());
    }

    public Optional<UUID> findLinkedUuid(String discordUserId) {
        return links.findById(discordUserId).map(l -> UUID.fromString(l.getMinecraftUuid()));
    }

    public Optional<String> findLinkedDiscordId(UUID minecraftUuid) {
        return links.findByMinecraftUuid(minecraftUuid.toString()).map(DiscordLink::getDiscordUserId);
    }

    @Transactional
    public boolean unlinkDiscord(String discordUserId) {
        if (!links.existsById(discordUserId)) {
            return false;
        }
        links.deleteById(discordUserId);
        return true;
    }

    @Transactional
    public boolean unlinkMinecraft(UUID minecraftUuid) {
        Optional<DiscordLink> found = links.findByMinecraftUuid(minecraftUuid.toString());
        found.ifPresent(links::delete);
        return found.isPresent();
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        pending.values().removeIf(p -> now.isAfter(p.expiresAt()));
    }

    private static String randomCode() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    public sealed interface RedeemResult {
        record Success(String discordTag) implements RedeemResult {
        }

        record InvalidCode() implements RedeemResult {
        }

        record AlreadyLinkedDiscord() implements RedeemResult {
        }

        record AlreadyLinkedMinecraft() implements RedeemResult {
        }

        static RedeemResult success(String discordTag) {
            return new Success(discordTag);
        }

        static RedeemResult invalidCode() {
            return new InvalidCode();
        }

        static RedeemResult alreadyLinkedDiscord() {
            return new AlreadyLinkedDiscord();
        }

        static RedeemResult alreadyLinkedMinecraft() {
            return new AlreadyLinkedMinecraft();
        }
    }
}
