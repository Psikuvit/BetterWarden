package me.psikuvit.betterWarden.core.service;

import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Resolves a username to a UUID via Mojang's profile API, for targeting players who have never
 * joined this server (so Bukkit has never cached them). Plain POJO like LangService - usable as
 * a Spring bean in HOST mode or `new`'d directly in CLIENT mode, neither of which should ever
 * block their calling thread on this, hence CompletableFuture throughout.
 */
@Service
public class MojangApiService {

    private static final String LOOKUP_URL = "https://api.mojang.com/users/profiles/minecraft/";
    private static final Pattern VALID_USERNAME = Pattern.compile("^\\w{1,16}$");
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private record CacheEntry(UUID uuid, Instant expiresAt) {
    }

    /** Never blocks the calling thread - completes exceptionally-free, empty Optional on any failure (not found, rate-limited, unreachable). */
    public CompletableFuture<Optional<UUID>> lookupUuid(String username) {
        if (!VALID_USERNAME.matcher(username).matches()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        String key = username.toLowerCase(java.util.Locale.ROOT);
        CacheEntry cached = cache.get(key);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return CompletableFuture.completedFuture(Optional.of(cached.uuid()));
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(LOOKUP_URL + username))
                .timeout(Duration.ofSeconds(5))
                .GET().build();
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> parse(key, response))
                .exceptionally(e -> Optional.empty());
    }

    private Optional<UUID> parse(String cacheKey, HttpResponse<String> response) {
        if (response.statusCode() != 200) {
            // 404/204 = no such account; anything else (429 rate-limited, 5xx) also just misses -
            // not cached, so the next attempt can retry instead of being stuck on a bad response.
            return Optional.empty();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(response.body(), Map.class);
            Object rawId = body.get("id");
            if (rawId == null) {
                return Optional.empty();
            }
            UUID uuid = UUID.fromString(insertDashes((String) rawId));
            cache.put(cacheKey, new CacheEntry(uuid, Instant.now().plus(CACHE_TTL)));
            return Optional.of(uuid);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** Mojang returns the id as 32 hex chars with no dashes - UUID.fromString needs the standard 8-4-4-4-12 form. */
    private static String insertDashes(String hex32) {
        return hex32.substring(0, 8) + "-" + hex32.substring(8, 12) + "-" + hex32.substring(12, 16)
                + "-" + hex32.substring(16, 20) + "-" + hex32.substring(20, 32);
    }
}
