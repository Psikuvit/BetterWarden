package me.psikuvit.betterWarden.core.ws;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import me.psikuvit.betterWarden.core.event.EventBus;
import me.psikuvit.betterWarden.core.event.PunishmentChangedEvent;
import me.psikuvit.betterWarden.core.network.dto.NodeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/** Push side of node sync: every PunishmentChangedEvent gets broadcast to every connected CLIENT node as a small invalidation notice - the node re-fetches that uuid over REST rather than this carrying the full payload. */
@Component
public class NodeWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(NodeWebSocketHandler.class);

    private final ObjectMapper objectMapper;
    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

    public NodeWebSocketHandler(ObjectMapper objectMapper, EventBus eventBus) {
        this.objectMapper = objectMapper;
        eventBus.subscribe(PunishmentChangedEvent.class, e -> broadcast(NodeEvent.punishmentChanged(e.uuid())));
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.info("Node connected: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.info("Node disconnected: {} ({})", session.getId(), status);
    }

    /** No per-node identity yet (see NodeAuthInterceptor) - just a live count, for /warden nodes. */
    public int connectedCount() {
        return sessions.size();
    }

    /**
     * Pushes a punishment-changed notice without going through the local EventBus - used by
     * warden-standalone's RedisPublisher for updates that originated on ANOTHER core replica.
     * Going through EventBus there would re-trigger RedisPublisher's own outbound forward and
     * ping-pong the update back and forth between replicas forever.
     */
    public void broadcastPunishmentChanged(String uuid) {
        broadcast(NodeEvent.punishmentChanged(uuid));
    }

    private void broadcast(NodeEvent event) {
        String json;
        try {
            json = objectMapper.writeValueAsString(event);
        } catch (JacksonException e) {
            log.error("Could not serialize node event", e);
            return;
        }
        TextMessage message = new TextMessage(json);
        for (WebSocketSession session : sessions) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(message);
                }
            } catch (IOException e) {
                log.warn("Failed to push to node {}: {}", session.getId(), e.getMessage());
            }
        }
    }
}
