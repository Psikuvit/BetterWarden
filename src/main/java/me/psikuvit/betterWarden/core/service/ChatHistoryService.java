package me.psikuvit.betterWarden.core.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/** In-memory rolling buffer of each online player's recent chat, for report chat-snapshots. Not persisted, not a chat log. */
@Service
public class ChatHistoryService {

    private static final int MAX_LINES = 20;

    private final Map<UUID, Deque<String>> buffers = new ConcurrentHashMap<>();

    public void record(UUID uuid, String message) {
        Deque<String> buffer = buffers.computeIfAbsent(uuid, k -> new ConcurrentLinkedDeque<>());
        buffer.addLast(Instant.now() + " " + message);
        while (buffer.size() > MAX_LINES) {
            buffer.pollFirst();
        }
    }

    public String snapshot(UUID uuid) {
        Deque<String> buffer = buffers.get(uuid);
        if (buffer == null || buffer.isEmpty()) {
            return "";
        }
        return String.join("\n", buffer);
    }
}
