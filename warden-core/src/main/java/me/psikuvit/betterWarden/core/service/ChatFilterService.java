package me.psikuvit.betterWarden.core.service;

import me.psikuvit.betterWarden.core.config.CoreConfig;
import me.psikuvit.betterWarden.core.config.EditionService;
import me.psikuvit.betterWarden.core.model.FilterAction;
import me.psikuvit.betterWarden.core.model.FilteredMessage;
import me.psikuvit.betterWarden.core.model.PunishmentType;
import me.psikuvit.betterWarden.core.repo.FilteredMessageRepository;
import me.psikuvit.betterWarden.core.util.ChatNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * docs/spec/01-CORE.txt ChatFilterService. Blocked-word hits cancel the message (FilterAction
 * .BLOCKED); everything else (ad detection, caps, spam, repeat) lets the message through but
 * queues it for staff review (FilterAction.FLAGGED) - "review queue instead of silent delete"
 * per spec, since an outright block on a heuristic (not an exact wordlist hit) risks false
 * positives a player can't see or contest.
 */
@Service
public class ChatFilterService {

    private static final Logger log = LoggerFactory.getLogger(ChatFilterService.class);

    /** Rule prefix marking it as a regex, matched against the raw message - see evaluate(). */
    private static final String REGEX_PREFIX = "regex:";

    // Loosely: a token, optional separator noise ("dot"/spaces/punctuation), a known TLD -
    // catches "site.com", "site . com", "site,com", "site dot com". Heuristic, not exhaustive.
    private static final Pattern AD_PATTERN = Pattern.compile(
            "\\b[a-z0-9-]{2,}\\s*(?:\\.|\\bdot\\b)\\s*(com|net|org|gg|io|shop|xyz|info|co|me|tv|to)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern IP_PATTERN = Pattern.compile("\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b");
    /** Cached in place of a malformed regex rule - never matches anything, by construction. */
    private static final Pattern NEVER_MATCH = Pattern.compile("(?!)");

    private final CoreConfig config;
    private final EditionService edition;
    private final FilteredMessageRepository filteredMessages;
    private final PunishmentService punishmentService;

    private record RecentMessage(Instant at, String normalized) {
    }

    /** Per-player rolling window for the spam/repeat throttle - not persisted, resets on restart, same tradeoff as ChatHistoryService. */
    private final Map<UUID, Deque<RecentMessage>> recent = new ConcurrentHashMap<>();

    /** Compiled once per distinct rule string, not once per message - blocked-words is checked on every chat message. */
    private final Map<String, Pattern> regexCache = new ConcurrentHashMap<>();

    public ChatFilterService(CoreConfig config, EditionService edition, FilteredMessageRepository filteredMessages,
                              PunishmentService punishmentService) {
        this.config = config;
        this.edition = edition;
        this.filteredMessages = filteredMessages;
        this.punishmentService = punishmentService;
    }

    public record FilterResult(FilterAction action, String matchedRule) {
        private static final FilterResult ALLOW = new FilterResult(null, null);

        public boolean isAllowed() {
            return action == null;
        }
    }

    /** Call once per chat message - evaluates every rule and, on a hit, both logs it and checks the auto-punish threshold. */
    @Transactional
    public FilterResult apply(UUID uuid, String playerName, String server, String rawMessage) {
        // docs/spec/08-TIERS-AND-LICENSING.txt - chat filter is paid-only, regardless of config.yml.
        if (edition.isFree()) {
            return FilterResult.ALLOW;
        }
        CoreConfig.ChatFilter cfg = config.getChatFilter();
        if (!cfg.isEnabled()) {
            return FilterResult.ALLOW;
        }

        FilterResult result = evaluate(uuid, cfg, rawMessage);
        if (result.isAllowed()) {
            return result;
        }

        FilteredMessage entry = new FilteredMessage();
        entry.setPlayerUuid(uuid.toString());
        entry.setPlayerName(playerName);
        entry.setServer(server);
        entry.setRawMessage(rawMessage);
        entry.setMatchedRule(result.matchedRule());
        entry.setAction(result.action());
        entry.setCreatedAt(Instant.now());
        filteredMessages.save(entry);

        maybeAutoPunish(uuid, playerName, server, cfg);
        return result;
    }

    private FilterResult evaluate(UUID uuid, CoreConfig.ChatFilter cfg, String rawMessage) {
        String normalized = ChatNormalizer.normalize(rawMessage);

        for (String rule : cfg.getBlockedWords()) {
            if (rule.startsWith(REGEX_PREFIX)) {
                if (compileRegexRule(rule).matcher(rawMessage).find()) {
                    return new FilterResult(FilterAction.BLOCKED, "blocked-word");
                }
                continue;
            }
            String normalizedRule = ChatNormalizer.normalize(rule);
            if (!normalizedRule.isEmpty() && normalized.contains(normalizedRule)) {
                return new FilterResult(FilterAction.BLOCKED, "blocked-word");
            }
        }

        if (cfg.isAdDetection() && (AD_PATTERN.matcher(rawMessage).find() || IP_PATTERN.matcher(rawMessage).find())) {
            return new FilterResult(FilterAction.FLAGGED, "ad-detection");
        }

        if (isExcessiveCaps(rawMessage, cfg)) {
            return new FilterResult(FilterAction.FLAGGED, "caps");
        }

        FilterResult throttleHit = checkThrottle(uuid, normalized, cfg);
        if (throttleHit != null) {
            return throttleHit;
        }

        return FilterResult.ALLOW;
    }

    /**
     * A malformed pattern is cached as NEVER_MATCH rather than left uncached - ConcurrentHashMap
     * .computeIfAbsent() records no mapping for a null result, which would otherwise retry
     * (and re-log) the same broken rule on every single chat message forever.
     */
    private Pattern compileRegexRule(String rule) {
        return regexCache.computeIfAbsent(rule, r -> {
            String patternStr = r.substring(REGEX_PREFIX.length());
            try {
                return Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE);
            } catch (PatternSyntaxException e) {
                log.warn("Invalid chat-filter regex rule '{}': {}", patternStr, e.getMessage());
                return NEVER_MATCH;
            }
        });
    }

    private boolean isExcessiveCaps(String raw, CoreConfig.ChatFilter cfg) {
        String letters = raw.replaceAll("[^a-zA-Z]", "");
        if (letters.length() < cfg.getCapsMinLength()) {
            return false;
        }
        long upper = letters.chars().filter(Character::isUpperCase).count();
        return (upper * 100 / letters.length()) >= cfg.getCapsThresholdPercent();
    }

    private FilterResult checkThrottle(UUID uuid, String normalized, CoreConfig.ChatFilter cfg) {
        Instant now = Instant.now();
        Deque<RecentMessage> window = recent.computeIfAbsent(uuid, k -> new ArrayDeque<>());
        synchronized (window) {
            window.addLast(new RecentMessage(now, normalized));
            Instant cutoff = now.minusSeconds(cfg.getSpamWindowSeconds());
            while (!window.isEmpty() && window.peekFirst().at().isBefore(cutoff)) {
                window.pollFirst();
            }
            while (window.size() > Math.max(cfg.getSpamMessageLimit(), cfg.getRepeatMessageLimit()) + 1) {
                window.pollFirst();
            }

            if (window.size() >= cfg.getSpamMessageLimit()) {
                return new FilterResult(FilterAction.FLAGGED, "spam");
            }

            if (countTrailingRepeats(window, normalized) >= cfg.getRepeatMessageLimit()) {
                return new FilterResult(FilterAction.FLAGGED, "repeat");
            }
        }
        return null;
    }

    private long countTrailingRepeats(Deque<RecentMessage> window, String normalized) {
        long count = 0;
        var it = window.descendingIterator();
        while (it.hasNext()) {
            RecentMessage m = it.next();
            if (m.normalized().equals(normalized)) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    private void maybeAutoPunish(UUID uuid, String playerName, String server, CoreConfig.ChatFilter cfg) {
        Instant windowStart = Instant.now().minus(Duration.ofMinutes(cfg.getAutoPunishWindowMinutes()));
        long hits = filteredMessages.countByPlayerUuidAndCreatedAtAfter(uuid.toString(), windowStart);
        if (hits < cfg.getAutoPunishThreshold()) {
            return;
        }
        if (punishmentService.activeMute(uuid).isPresent()) {
            return;
        }
        punishmentService.issue(uuid, playerName, PunishmentType.TEMPMUTE,
                "Automatic mute: repeated chat filter violations",
                null, Duration.ofMinutes(cfg.getAutoPunishDurationMinutes()), false, server);
    }
}
