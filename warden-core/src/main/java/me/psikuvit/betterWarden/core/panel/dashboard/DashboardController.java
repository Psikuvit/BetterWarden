package me.psikuvit.betterWarden.core.panel.dashboard;

import me.psikuvit.betterWarden.core.model.AuditLogEntry;
import me.psikuvit.betterWarden.core.model.Punishment;
import me.psikuvit.betterWarden.core.model.PunishmentType;
import me.psikuvit.betterWarden.core.model.ReportStatus;
import me.psikuvit.betterWarden.core.model.TicketStatus;
import me.psikuvit.betterWarden.core.platform.PlatformBridge;
import me.psikuvit.betterWarden.core.repo.AuditLogRepository;
import me.psikuvit.betterWarden.core.repo.PunishmentRepository;
import me.psikuvit.betterWarden.core.repo.ReportRepository;
import me.psikuvit.betterWarden.core.repo.TicketRepository;
import me.psikuvit.betterWarden.core.ws.NodeWebSocketHandler;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Guarded by SecurityConfig's /api/panel/** -> authenticated() rule - no per-role check yet, any logged-in account can read this. */
@RestController
public class DashboardController {

    private static final int ACTIVITY_DAYS = 30;
    private static final int RECENT_ACTIONS_LIMIT = 20;

    private final PlatformBridge bridge;
    private final ReportRepository reports;
    private final TicketRepository tickets;
    private final PunishmentRepository punishments;
    private final AuditLogRepository auditLog;
    private final NodeWebSocketHandler nodeHub;

    public DashboardController(PlatformBridge bridge, ReportRepository reports, TicketRepository tickets,
                                PunishmentRepository punishments, AuditLogRepository auditLog, NodeWebSocketHandler nodeHub) {
        this.bridge = bridge;
        this.reports = reports;
        this.tickets = tickets;
        this.punishments = punishments;
        this.auditLog = auditLog;
        this.nodeHub = nodeHub;
    }

    @GetMapping("/api/panel/dashboard")
    public DashboardResponse dashboard() {
        List<DashboardResponse.RecentAction> recentActions = auditLog
                .findAllByOrderByAtDesc(PageRequest.of(0, RECENT_ACTIONS_LIMIT, Sort.unsorted()))
                .stream()
                .map(this::toRecentAction)
                .toList();

        return new DashboardResponse(
                bridge.onlinePlayers().size(),
                reports.countByStatus(ReportStatus.OPEN),
                tickets.countByStatusNot(TicketStatus.CLOSED),
                punishments.countByActiveTrueAndTypeIn(List.copyOf(PunishmentType.BAN_TYPES)),
                punishments.countByActiveTrueAndTypeIn(List.copyOf(PunishmentType.MUTE_TYPES)),
                0, // pendingAppeals - no appeals system yet, see class doc
                nodeHub.connectedCount(),
                recentActions,
                punishmentActivity());
    }

    private DashboardResponse.RecentAction toRecentAction(AuditLogEntry e) {
        return new DashboardResponse.RecentAction(e.getId(), e.getActor(), e.getActorType().name(), e.getAction(), e.getTarget(), e.getAt());
    }

    private List<DashboardResponse.DailyCount> punishmentActivity() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(ACTIVITY_DAYS));
        Map<LocalDate, Long> byDay = new TreeMap<>();

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        for (int i = 0; i < ACTIVITY_DAYS; i++) {
            byDay.put(today.minusDays(i), 0L);
        }
        for (Punishment p : punishments.findByIssuedAtAfter(cutoff)) {
            LocalDate day = p.getIssuedAt().atZone(ZoneOffset.UTC).toLocalDate();
            byDay.merge(day, 1L, Long::sum);
        }

        return byDay.entrySet().stream()
                .map(e -> new DashboardResponse.DailyCount(e.getKey(), e.getValue()))
                .toList();
    }
}
