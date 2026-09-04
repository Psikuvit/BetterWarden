package me.psikuvit.betterWarden.core.service;

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

    public ReportService(ReportRepository reports, ChatHistoryService chatHistory, AuditLogRepository auditLog) {
        this.reports = reports;
        this.chatHistory = chatHistory;
        this.auditLog = auditLog;
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
        return reports.save(report);
    }

    @Transactional
    public Optional<Report> claim(Long id, UUID staffUuid) {
        Optional<Report> found = reports.findById(id);
        found.ifPresent(r -> {
            r.setStatus(ReportStatus.CLAIMED);
            r.setClaimedBy(staffUuid == null ? null : staffUuid.toString());
            audit(staffUuid, "REPORT_CLAIM", id.toString());
        });
        return found;
    }

    @Transactional
    public Optional<Report> dismiss(Long id, UUID staffUuid) {
        Optional<Report> found = reports.findById(id);
        found.ifPresent(r -> {
            r.setStatus(ReportStatus.DISMISSED);
            r.setClosedAt(Instant.now());
            audit(staffUuid, "REPORT_DISMISS", id.toString());
        });
        return found;
    }

    @Transactional
    public Optional<Report> close(Long id, UUID staffUuid) {
        Optional<Report> found = reports.findById(id);
        found.ifPresent(r -> {
            r.setStatus(ReportStatus.CLOSED);
            r.setClosedAt(Instant.now());
            audit(staffUuid, "REPORT_CLOSE", id.toString());
        });
        return found;
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
