package me.psikuvit.betterWarden.core.service;

import me.psikuvit.betterWarden.core.event.EventBus;
import me.psikuvit.betterWarden.core.event.PunishmentChangedEvent;
import me.psikuvit.betterWarden.core.event.PunishmentIssuedEvent;
import me.psikuvit.betterWarden.core.event.PunishmentRevokedEvent;
import me.psikuvit.betterWarden.core.model.ActorType;
import me.psikuvit.betterWarden.core.model.AuditLogEntry;
import me.psikuvit.betterWarden.core.model.Escalation;
import me.psikuvit.betterWarden.core.model.Player;
import me.psikuvit.betterWarden.core.model.Punishment;
import me.psikuvit.betterWarden.core.model.PunishmentRevoke;
import me.psikuvit.betterWarden.core.model.PunishmentTemplate;
import me.psikuvit.betterWarden.core.model.PunishmentType;
import me.psikuvit.betterWarden.core.platform.PlatformBridge;
import me.psikuvit.betterWarden.core.repo.AuditLogRepository;
import me.psikuvit.betterWarden.core.repo.EscalationRepository;
import me.psikuvit.betterWarden.core.repo.PunishmentRepository;
import me.psikuvit.betterWarden.core.repo.PunishmentRevokeRepository;
import me.psikuvit.betterWarden.core.util.DurationParser;
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
public class PunishmentService implements PunishmentGateway {

    private final PunishmentRepository punishments;
    private final PunishmentRevokeRepository revokes;
    private final EscalationRepository escalations;
    private final AuditLogRepository auditLog;
    private final PlatformBridge bridge;
    private final PlayerTrackingService playerTracking;
    private final PunishmentCache cache;
    private final EventBus eventBus;
    private final LangService lang;

    public PunishmentService(PunishmentRepository punishments,
                              PunishmentRevokeRepository revokes, EscalationRepository escalations,
                              AuditLogRepository auditLog, PlatformBridge bridge, PlayerTrackingService playerTracking,
                              PunishmentCache cache, EventBus eventBus, LangService lang) {
        this.punishments = punishments;
        this.revokes = revokes;
        this.escalations = escalations;
        this.auditLog = auditLog;
        this.bridge = bridge;
        this.playerTracking = playerTracking;
        this.cache = cache;
        this.eventBus = eventBus;
        this.lang = lang;
    }

    @Transactional
    public Punishment issue(UUID targetUuid, String targetName, PunishmentType type, String reason,
                             UUID staffUuid, Duration duration, boolean silent, String server) {
        return issueInternal(targetUuid, targetName, type, reason, staffUuid, duration, silent, server, null);
    }

    /**
     * Applies a stored template, resolving its escalation ladder first: if the template belongs to an
     * escalation group, the Nth offence in that group overrides the template's own type/duration with
     * whatever rung matches (capped at the highest defined rung for anything beyond it).
     */
    @Transactional
    public Punishment issueFromTemplate(UUID targetUuid, String targetName, PunishmentTemplate template,
                                         UUID staffUuid, boolean silent, String server) {
        PunishmentType type = template.getType();
        Duration duration = template.getDuration() == null ? null : DurationParser.parse(template.getDuration());

        if (template.getEscalationGroup() != null) {
            long priorOffences = punishments.countByUuidAndTemplateEscalationGroup(targetUuid.toString(), template.getEscalationGroup());
            Optional<Escalation> rung = resolveEscalationRung(template.getEscalationGroup(), (int) priorOffences + 1);
            if (rung.isPresent()) {
                type = rung.get().getType();
                duration = rung.get().getDuration() == null ? null : DurationParser.parse(rung.get().getDuration());
            }
        }

        return issueInternal(targetUuid, targetName, type, template.getReason(), staffUuid, duration, silent, server, template.getId());
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
        eventBus.publish(new PunishmentRevokedEvent(punishment));
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

    /** Console-issued punishments have a null staffUuid; a resolved name falls back to the raw UUID if it's not in players (e.g. wiped/never joined). */
    public String resolveStaffName(Punishment punishment) {
        String staffUuid = punishment.getStaffUuid();
        if (staffUuid == null) {
            return "Console";
        }
        return playerTracking.find(UUID.fromString(staffUuid)).map(Player::getLastName).orElse(staffUuid);
    }

    public List<Punishment> history(UUID uuid, int limit) {
        return punishments.findByUuidOrderByIssuedAtDesc(uuid.toString(),
                PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "issuedAt"))).getContent();
    }

    /** Highest-defined rung whose offenceNumber is <= the actual offence number - the ladder caps out, it doesn't stop. */
    private Optional<Escalation> resolveEscalationRung(String groupKey, int offenceNumber) {
        Escalation best = null;
        for (Escalation rung : escalations.findByGroupKeyOrderByOffenceNumberAsc(groupKey)) {
            if (rung.getOffenceNumber() <= offenceNumber) {
                best = rung;
            }
        }
        return Optional.ofNullable(best);
    }

    private Punishment issueInternal(UUID targetUuid, String targetName, PunishmentType type, String reason,
                                      UUID staffUuid, Duration duration, boolean silent, String server, Long templateId) {
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
        punishment.setTemplateId(templateId);
        punishment = punishments.save(punishment);

        audit(staffUuid, "PUNISH_" + type, targetUuid.toString(),
                "reason=" + reason + ", duration=" + (duration == null ? "perm" : duration));

        eventBus.publish(new PunishmentChangedEvent(targetUuid.toString()));
        eventBus.publish(new PunishmentIssuedEvent(punishment));
        applyImmediateEffect(targetUuid, punishment);
        return punishment;
    }

    private void applyImmediateEffect(UUID targetUuid, Punishment punishment) {
        switch (punishment.getType()) {
            case BAN, TEMPBAN, IPBAN, KICK -> {
                if (bridge.isOnline(targetUuid)) {
                    bridge.kick(targetUuid, kickMessage(punishment));
                }
            }
            case MUTE, TEMPMUTE ->
                    bridge.message(targetUuid, lang.get("punish.muted-message", punishment.getReason()));
            case WARN -> bridge.message(targetUuid, lang.get("punish.warned-message", punishment.getReason()));
        }
    }

    private String kickMessage(Punishment p) {
        String duration = p.isPermanent() ? lang.get("punish.permanent") : lang.get("punish.until", p.getExpiresAt());
        return lang.get("punish.kicked-message", p.getReason(), duration);
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
