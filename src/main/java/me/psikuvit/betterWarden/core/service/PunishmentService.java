package me.psikuvit.betterWarden.core.service;

import me.psikuvit.betterWarden.core.model.ActorType;
import me.psikuvit.betterWarden.core.model.AuditLogEntry;
import me.psikuvit.betterWarden.core.model.Punishment;
import me.psikuvit.betterWarden.core.model.PunishmentRevoke;
import me.psikuvit.betterWarden.core.model.PunishmentType;
import me.psikuvit.betterWarden.core.event.EventBus;
import me.psikuvit.betterWarden.core.event.PunishmentChangedEvent;
import me.psikuvit.betterWarden.core.platform.PlatformBridge;
import me.psikuvit.betterWarden.core.repo.AuditLogRepository;
import me.psikuvit.betterWarden.core.repo.PlayerRepository;
import me.psikuvit.betterWarden.core.repo.PunishmentRepository;
import me.psikuvit.betterWarden.core.repo.PunishmentRevokeRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PunishmentService {

    private final PlayerRepository players;
    private final PunishmentRepository punishments;
    private final PunishmentRevokeRepository revokes;
    private final AuditLogRepository auditLog;
    private final PlatformBridge bridge;
    private final PlayerTrackingService playerTracking;
    private final PunishmentCache cache;
    private final EventBus eventBus;

    public PunishmentService(PlayerRepository players, PunishmentRepository punishments,
                              PunishmentRevokeRepository revokes, AuditLogRepository auditLog,
                              PlatformBridge bridge, PlayerTrackingService playerTracking,
                              PunishmentCache cache, EventBus eventBus) {
        this.players = players;
        this.punishments = punishments;
        this.revokes = revokes;
        this.auditLog = auditLog;
        this.bridge = bridge;
        this.playerTracking = playerTracking;
        this.cache = cache;
        this.eventBus = eventBus;
    }

    @Transactional
    public Punishment issue(UUID targetUuid, String targetName, PunishmentType type, String reason,
                             UUID staffUuid, Duration duration, boolean silent, String server) {
        playerTracking.ensurePlayerExists(targetUuid, targetName);

        for (Punishment existing : punishments.findByUuidAndTypeAndActiveTrue(targetUuid.toString(), type)) {
            existing.setActive(false);
        }

        Instant now = Instant.now();
        Punishment punishment = new Punishment();
        punishment.setUuid(targetUuid.toString());
        punishment.setType(type);
        punishment.setReason(reason);
        punishment.setStaffUuid(staffUuid == null ? null : staffUuid.toString());
        punishment.setIssuedAt(now);
        punishment.setExpiresAt(duration == null ? null : now.plus(duration));
        punishment.setActive(true);
        punishment.setSilent(silent);
        punishment.setServer(server);
        punishment = punishments.save(punishment);

        audit(staffUuid, "PUNISH_" + type, targetUuid.toString(),
                "reason=" + reason + ", duration=" + (duration == null ? "perm" : duration));

        eventBus.publish(new PunishmentChangedEvent(targetUuid.toString()));
        applyImmediateEffect(targetUuid, punishment);
        return punishment;
    }

    /** Bans the target's last known IP hash, not just their UUID. */
    @Transactional
    public Punishment issueIpBan(UUID targetUuid, String targetName, String reason, UUID staffUuid,
                                  Duration duration, boolean silent, String server) {
        Punishment punishment = issue(targetUuid, targetName, PunishmentType.IPBAN, reason, staffUuid, duration, silent, server);
        playerTracking.lastIpHash(targetUuid).ifPresent(punishment::setIpHash);
        punishments.save(punishment);
        eventBus.publish(new PunishmentChangedEvent(targetUuid.toString()));
        return punishment;
    }

    @Transactional
    public Optional<Punishment> revoke(Long punishmentId, UUID staffUuid, String reason) {
        Optional<Punishment> found = punishments.findById(punishmentId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Punishment punishment = found.get();
        punishment.setActive(false);

        PunishmentRevoke revoke = new PunishmentRevoke();
        revoke.setPunishmentId(punishmentId);
        revoke.setStaffUuid(staffUuid == null ? null : staffUuid.toString());
        revoke.setReason(reason);
        revoke.setRevokedAt(Instant.now());
        revokes.save(revoke);

        audit(staffUuid, "REVOKE_" + punishment.getType(), punishment.getUuid(), "reason=" + reason);
        eventBus.publish(new PunishmentChangedEvent(punishment.getUuid()));
        return Optional.of(punishment);
    }

    public List<Punishment> activePunishments(UUID uuid) {
        return cache.active(uuid.toString());
    }

    public Optional<Punishment> activeBan(UUID uuid) {
        return cache.activeBan(uuid.toString());
    }

    public Optional<Punishment> activeMute(UUID uuid) {
        return cache.activeMute(uuid.toString());
    }

    public Optional<Punishment> activeIpBan(String ipHash) {
        return cache.activeIpBan(ipHash);
    }

    public List<Punishment> history(UUID uuid, int limit) {
        return punishments.findByUuidOrderByIssuedAtDesc(uuid.toString(),
                PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "issuedAt"))).getContent();
    }

    private void applyImmediateEffect(UUID targetUuid, Punishment punishment) {
        switch (punishment.getType()) {
            case BAN, TEMPBAN, IPBAN, KICK -> {
                if (bridge.isOnline(targetUuid)) {
                    bridge.kick(targetUuid, kickMessage(punishment));
                }
            }
            case MUTE, TEMPMUTE ->
                    bridge.message(targetUuid, "<red>You have been muted: <gray>" + punishment.getReason());
            case WARN -> bridge.message(targetUuid, "<yellow>You have been warned: <gray>" + punishment.getReason());
        }
    }

    private String kickMessage(Punishment p) {
        String duration = p.isPermanent() ? "Permanent" : "Until " + p.getExpiresAt();
        return "<red>You are banned.\n<gray>Reason: " + p.getReason() + "\n<gray>Duration: " + duration;
    }

    private void audit(UUID staffUuid, String action, String target, String payload) {
        AuditLogEntry entry = new AuditLogEntry();
        entry.setActor(staffUuid == null ? "CONSOLE" : staffUuid.toString());
        entry.setActorType(staffUuid == null ? ActorType.CONSOLE : ActorType.STAFF);
        entry.setAction(action);
        entry.setTarget(target);
        entry.setPayload(payload);
        entry.setAt(Instant.now());
        auditLog.save(entry);
    }
}
