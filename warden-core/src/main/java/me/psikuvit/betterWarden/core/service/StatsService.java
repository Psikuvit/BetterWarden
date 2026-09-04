package me.psikuvit.betterWarden.core.service;

import me.psikuvit.betterWarden.core.model.Punishment;
import me.psikuvit.betterWarden.core.model.Report;
import me.psikuvit.betterWarden.core.model.Ticket;
import me.psikuvit.betterWarden.core.repo.AuditLogRepository;
import me.psikuvit.betterWarden.core.repo.PunishmentRepository;
import me.psikuvit.betterWarden.core.repo.ReportRepository;
import me.psikuvit.betterWarden.core.repo.TicketMessageRepository;
import me.psikuvit.betterWarden.core.repo.TicketRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * docs/spec/01-CORE.txt StatsService. Response time and per-staff rollups are computed from
 * existing data (audit_log, ticket_messages), not a separate persisted rollup table - same
 * "compute in Java over raw rows" choice as DashboardController's activity chart, and cheap
 * enough at MVP scale. Overturn rate is not computed - there's no appeals system yet (PLAN.md
 * Stage 5), same honest-gap treatment as DashboardResponse.pendingAppeals.
 */
@Service
public class StatsService {

    private static final List<String> REPORT_RESPONSE_ACTIONS = List.of("REPORT_CLAIM");

    private final PunishmentRepository punishments;
    private final ReportRepository reports;
    private final TicketRepository tickets;
    private final TicketMessageRepository ticketMessages;
    private final AuditLogRepository auditLog;

    public StatsService(PunishmentRepository punishments, ReportRepository reports, TicketRepository tickets,
                         TicketMessageRepository ticketMessages, AuditLogRepository auditLog) {
        this.punishments = punishments;
        this.reports = reports;
        this.tickets = tickets;
        this.ticketMessages = ticketMessages;
        this.auditLog = auditLog;
    }

    public record StaffStat(String staffUuid, long punishmentsIssued, long reportsHandled,
                             long ticketsHandled, Long avgResponseSeconds) {
    }

    /** server null/blank = network-wide aggregation (spec's "network aggregation toggle"); otherwise scoped to that server's own rows. */
    public List<StaffStat> perStaff(Instant since, String server) {
        List<Punishment> recentPunishments = filterByServer(punishments.findByIssuedAtAfter(since), server, Punishment::getServer);
        Map<String, Long> punishCounts = recentPunishments.stream()
                .filter(p -> p.getStaffUuid() != null)
                .collect(Collectors.groupingBy(Punishment::getStaffUuid, Collectors.counting()));

        List<Report> recentReports = filterByServer(reports.findByCreatedAtAfter(since), server, Report::getServer);
        Map<String, Long> reportCounts = recentReports.stream()
                .filter(r -> r.getClaimedBy() != null)
                .collect(Collectors.groupingBy(Report::getClaimedBy, Collectors.counting()));
        Map<String, List<Long>> responseSeconds = new HashMap<>();
        for (Report r : recentReports) {
            if (r.getClaimedBy() == null) {
                continue;
            }
            auditLog.findByTargetAndActionInOrderByAtAsc(r.getId().toString(), REPORT_RESPONSE_ACTIONS).stream()
                    .findFirst()
                    .ifPresent(entry -> responseSeconds.computeIfAbsent(r.getClaimedBy(), k -> new ArrayList<>())
                            .add(Duration.between(r.getCreatedAt(), entry.getAt()).getSeconds()));
        }

        // Tickets have no server column (they're not tied to one backend - a ticket can span the whole network), so the toggle doesn't apply here.
        List<Ticket> recentTickets = tickets.findByCreatedAtAfter(since);
        Map<String, Long> ticketCounts = recentTickets.stream()
                .filter(t -> t.getAssignee() != null)
                .collect(Collectors.groupingBy(Ticket::getAssignee, Collectors.counting()));
        for (Ticket t : recentTickets) {
            ticketMessages.findByTicketIdOrderBySentAtAsc(t.getId()).stream()
                    .filter(m -> !m.getAuthor().equals(t.getOpener()))
                    .findFirst()
                    .ifPresent(m -> responseSeconds.computeIfAbsent(m.getAuthor(), k -> new ArrayList<>())
                            .add(Duration.between(t.getCreatedAt(), m.getSentAt()).getSeconds()));
        }

        Set<String> allStaff = new HashSet<>();
        allStaff.addAll(punishCounts.keySet());
        allStaff.addAll(reportCounts.keySet());
        allStaff.addAll(ticketCounts.keySet());

        return allStaff.stream()
                .map(staffUuid -> {
                    List<Long> staffResponses = responseSeconds.getOrDefault(staffUuid, List.of());
                    Long avg = staffResponses.isEmpty() ? null
                            : (long) staffResponses.stream().mapToLong(Long::longValue).average().orElse(0);
                    return new StaffStat(staffUuid,
                            punishCounts.getOrDefault(staffUuid, 0L),
                            reportCounts.getOrDefault(staffUuid, 0L),
                            ticketCounts.getOrDefault(staffUuid, 0L),
                            avg);
                })
                .sorted(Comparator.comparingLong(StaffStat::punishmentsIssued).reversed())
                .toList();
    }

    public record WeeklyDigest(Instant periodStart, Instant periodEnd, long totalPunishments,
                                long totalReports, long totalTickets, List<StaffStat> topStaff) {
    }

    /** Not delivered anywhere yet (no email/Discord channel exists) - a real, computed summary a caller can render or log, not a scheduled push. */
    public WeeklyDigest weeklyDigest() {
        Instant end = Instant.now();
        Instant start = end.minus(Duration.ofDays(7));
        List<StaffStat> staff = perStaff(start, null);
        long totalPunishments = staff.stream().mapToLong(StaffStat::punishmentsIssued).sum();
        long totalReports = staff.stream().mapToLong(StaffStat::reportsHandled).sum();
        long totalTickets = staff.stream().mapToLong(StaffStat::ticketsHandled).sum();
        List<StaffStat> topFive = staff.stream().limit(5).toList();
        return new WeeklyDigest(start, end, totalPunishments, totalReports, totalTickets, topFive);
    }

    private <T> List<T> filterByServer(List<T> items, String server, Function<T, String> serverOf) {
        if (server == null || server.isBlank()) {
            return items;
        }
        return items.stream().filter(item -> server.equals(serverOf.apply(item))).toList();
    }
}
