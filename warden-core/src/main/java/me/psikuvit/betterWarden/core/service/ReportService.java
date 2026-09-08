package me.psikuvit.betterWarden.core.service;

import me.psikuvit.betterWarden.core.event.EventBus;
import me.psikuvit.betterWarden.core.event.ReportChangedEvent;
import me.psikuvit.betterWarden.core.event.ReportCreatedEvent;
import me.psikuvit.betterWarden.core.model.ActorType;
import me.psikuvit.betterWarden.core.model.AuditLogEntry;
import me.psikuvit.betterWarden.core.model.Report;
import me.psikuvit.betterWarden.core.model.ReportStatus;
import me.psikuvit.betterWarden.core.repo.AuditLogRepository;
import me.psikuvit.betterWarden.core.repo.ReportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ReportService {

    private final ReportRepository reports;
    private final ChatHistoryService chatHistory;
    private final AuditLogRepository auditLog;
    private final EventBus eventBus;

    public ReportService(ReportRepository reports, ChatHistoryService chatHistory, AuditLogRepository auditLog, EventBus eventBus) {
        this.reports = reports;
        this.chatHistory = chatHistory;
        this.auditLog = auditLog;
        this.eventBus = eventBus;
    }

    @Transactional
    public Report create(UUID reporterUuid, UUID targetUuid, String reason, String category, String location, String server) {
        Report report = new Report();
        report.setReporter(reporterUuid.toString());
        report.setTarget(targetUuid.toString());
        report.setReason(reason);
        report.setCategory(category);
        report.setStatus(ReportStatus.OPEN);
        report.setCreatedAt(Instant.now());
        report.setChatSnapshot(chatHistory.snapshot(targetUuid));
        report.setLocation(location);
        report.setServer(server);
        Report saved = reports.save(report);
        eventBus.publish(new ReportCreatedEvent(saved));
        return saved;
    }

    @Transactional
    public Optional<Report> claim(Long id, UUID staffUuid) {
        Optional<Report> found = reports.findById(id);
        found.ifPresent(r -> {
            r.setStatus(ReportStatus.CLAIMED);
            r.setClaimedBy(staffUuid == null ? null : staffUuid.toString());
            audit(staffUuid, "REPORT_CLAIM", id.toString(), null);
            eventBus.publish(new ReportChangedEvent(r));
        });
        return found;
    }

    public Optional<Report> dismiss(Long id, UUID staffUuid) {
        return dismiss(id, staffUuid, null);
    }

    @Transactional
    public Optional<Report> dismiss(Long id, UUID staffUuid, String reason) {
        Optional<Report> found = reports.findById(id);
        found.ifPresent(r -> {
            r.setStatus(ReportStatus.DISMISSED);
            r.setClosedAt(Instant.now());
            audit(staffUuid, "REPORT_DISMISS", id.toString(), reason);
            eventBus.publish(new ReportChangedEvent(r));
        });
        return found;
    }

    @Transactional
    public Optional<Report> close(Long id, UUID staffUuid) {
        Optional<Report> found = reports.findById(id);
        found.ifPresent(r -> {
            r.setStatus(ReportStatus.CLOSED);
            r.setClosedAt(Instant.now());
            audit(staffUuid, "REPORT_CLOSE", id.toString(), null);
            eventBus.publish(new ReportChangedEvent(r));
        });
        return found;
    }

    /** Records which Discord message backs this report's embed, so later status changes edit it in place. */
    @Transactional
    public void attachDiscordMessage(Long id, String channelId, String messageId) {
        reports.findById(id).ifPresent(r -> {
            r.setDiscordChannelId(channelId);
            r.setDiscordMessageId(messageId);
            reports.save(r);
        });
    }

    /** Reports open longer than maxAge get auto-closed - call periodically. */
    @Transactional
    public int closeStale(java.time.Duration maxAge) {
        List<Report> stale = reports.findByStatusAndCreatedAtBefore(ReportStatus.OPEN, Instant.now().minus(maxAge));
        for (Report r : stale) {
            r.setStatus(ReportStatus.CLOSED);
            r.setClosedAt(Instant.now());
        }
        return stale.size();
    }

    public List<Report> list(ReportStatus status) {
        return status == null ? reports.findAllByOrderByCreatedAtDesc() : reports.findByStatusOrderByCreatedAtDesc(status);
    }

    public Optional<Report> find(Long id) {
        return reports.findById(id);
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
