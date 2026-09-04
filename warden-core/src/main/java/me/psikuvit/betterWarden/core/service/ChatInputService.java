package me.psikuvit.betterWarden.core.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Pending "type your answer in chat" callbacks, keyed by player. A pragmatic substitute for an
 * anvil-input GUI: much simpler to get right without a way to visually verify anvil rename-text
 * capture blind. See ChatInputListener for where submissions actually arrive.
 */
@Service
public class ChatInputService {

    private final Map<UUID, Consumer<String>> pending = new ConcurrentHashMap<>();

    public void awaitInput(UUID uuid, Consumer<String> callback) {
        pending.put(uuid, callback);
    }

    public boolean hasPending(UUID uuid) {
        return pending.containsKey(uuid);
    }

    public void submit(UUID uuid, String text) {
        Consumer<String> callback = pending.remove(uuid);
        if (callback != null) {
            callback.accept(text);
        }
    }

    public void cancel(UUID uuid) {
        pending.remove(uuid);
    }
}
