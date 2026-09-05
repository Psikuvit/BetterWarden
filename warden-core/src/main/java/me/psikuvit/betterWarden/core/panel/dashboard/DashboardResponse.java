package me.psikuvit.betterWarden.core.panel.dashboard;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** docs/spec/04-PANEL.txt §4 DASHBOARD. */
public record DashboardResponse(int onlinePlayers, long openReports, long openTickets, long activeBans,
                                 long activeMutes, long pendingAppeals, int connectedNodes,
                                 List<RecentAction> recentActions, List<DailyCount> punishmentActivity) {

    public record RecentAction(Long id, String actor, String actorType, String action, String target, Instant at) {
    }

    public record DailyCount(LocalDate date, long count) {
    }
}
