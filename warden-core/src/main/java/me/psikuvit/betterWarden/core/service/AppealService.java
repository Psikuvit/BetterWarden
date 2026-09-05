package me.psikuvit.betterWarden.core.service;

import me.psikuvit.betterWarden.core.model.ActorType;
import me.psikuvit.betterWarden.core.model.Appeal;
import me.psikuvit.betterWarden.core.model.AppealStatus;
import me.psikuvit.betterWarden.core.model.AuditLogEntry;
import me.psikuvit.betterWarden.core.repo.AppealRepository;
import me.psikuvit.betterWarden.core.repo.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** docs/spec/04-PANEL.txt §4 APPEALS - review queue only, see Appeal.java for what's not built yet. */
@Service
public class AppealService {

    private final AppealRepository appeals;
    private final PunishmentService punishmentService;
    private final AuditLogRepository auditLog;

    public AppealService(AppealRepository appeals, PunishmentService punishmentService, AuditLogRepository auditLog) {
        this.appeals = appeals;
        this.punishmentService = punishmentService;
        this.auditLog = auditLog;
    }

    public List<Appeal> list(AppealStatus status) {
        return status == null ? appeals.findAllByOrderByCreatedAtDesc() : appeals.findByStatusOrderByCreatedAtDesc(status);
    }

    public Optional<Appeal> find(Long id) {
        return appeals.findById(id);
    }

    public long pendingCount() {
        return appeals.countByStatus(AppealStatus.PENDING);
    }

    /** Approving an appeal auto-revokes the punishment it's against - spec §4: "Approve (auto-unban)". */
    @Transactional
    public Optional<Appeal> approve(Long id, UUID staffUuid, String note) {
        Optional<Appeal> found = appeals.findById(id);
        found.ifPresent(a -> {
            punishmentService.revoke(a.getPunishmentId(), staffUuid, "Appeal approved" + (note == null || note.isBlank() ? "" : ": " + note));
            a.setStatus(AppealStatus.APPROVED);
            a.setReviewedBy(staffUuid == null ? "CONSOLE" : staffUuid.toString());
            a.setReviewNote(note);
            a.setReviewedAt(Instant.now());
            audit(staffUuid, "APPEAL_APPROVE", id.toString());
        });
        return found;
    }

    @Transactional
    public Optional<Appeal> deny(Long id, UUID staffUuid, String reason) {
        Optional<Appeal> found = appeals.findById(id);
        found.ifPresent(a -> {
            a.setStatus(AppealStatus.DENIED);
            a.setReviewedBy(staffUuid == null ? "CONSOLE" : staffUuid.toString());
            a.setReviewNote(reason);
            a.setReviewedAt(Instant.now());
            audit(staffUuid, "APPEAL_DENY", id.toString());
        });
        return found;
    }

    @Transactional
    public Optional<Appeal> requestMoreInfo(Long id, UUID staffUuid, String note) {
        Optional<Appeal> found = appeals.findById(id);
        found.ifPresent(a -> {
            a.setStatus(AppealStatus.MORE_INFO);
            a.setReviewedBy(staffUuid == null ? "CONSOLE" : staffUuid.toString());
            a.setReviewNote(note);
            a.setReviewedAt(Instant.now());
            audit(staffUuid, "APPEAL_MORE_INFO", id.toString());
        });
        return found;
    }

    private void audit(UUID staffUuid, String action, String target) {
        AuditLogEntry entry = new AuditLogEntry();
        entry.setActor(staffUuid == null ? "CONSOLE" : staffUuid.toString());
        entry.setActorType(staffUuid == null ? ActorType.CONSOLE : ActorType.STAFF);
        entry.setAction(action);
        entry.setTarget(target);
        entry.setAt(Instant.now());
        auditLog.save(entry);
    }
}
