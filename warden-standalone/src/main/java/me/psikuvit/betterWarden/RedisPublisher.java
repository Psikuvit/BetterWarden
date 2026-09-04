package me.psikuvit.betterWarden;

import me.psikuvit.betterWarden.core.event.EventBus;
import me.psikuvit.betterWarden.core.event.PunishmentChangedEvent;
import me.psikuvit.betterWarden.core.service.PunishmentCache;
import me.psikuvit.betterWarden.core.ws.NodeWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Cross-instance sync for running more than one warden-standalone replica behind a load balancer
 * (nothing in Stages 1-3 needed this - single embedded core per network). One-directional in
 * each place on purpose: local writes forward to Redis (via the EventBus subscription below),
 * but a message ARRIVING from Redis updates the local cache/WS hub directly, never back through
 * the EventBus - otherwise every replica would re-forward every other replica's update forever.
 */
@Component
public class RedisPublisher implements MessageListener {

    public static final String CHANNEL = "warden:punishment-changed";

    private static final Logger log = LoggerFactory.getLogger(RedisPublisher.class);

    private final StringRedisTemplate redis;
    private final PunishmentCache cache;
    private final NodeWebSocketHandler nodeHub;

    public RedisPublisher(StringRedisTemplate redis, EventBus eventBus, PunishmentCache cache, NodeWebSocketHandler nodeHub) {
        this.redis = redis;
        this.cache = cache;
        this.nodeHub = nodeHub;
        eventBus.subscribe(PunishmentChangedEvent.class, e -> redis.convertAndSend(CHANNEL, e.uuid()));
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String uuid = redis.getStringSerializer().deserialize(message.getBody());
        if (uuid == null) {
            return;
        }
        cache.refresh(uuid);
        nodeHub.broadcastPunishmentChanged(uuid);
        log.debug("Applied punishment change for {} from another core replica.", uuid);
    }
}
