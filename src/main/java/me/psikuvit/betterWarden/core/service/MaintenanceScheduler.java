package me.psikuvit.betterWarden.core.service;

import me.psikuvit.betterWarden.core.config.CoreConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Runs on Spring's own scheduler thread, never the game's main thread. */
@Component
public class MaintenanceScheduler {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceScheduler.class);

    private final ReportService reportService;
    private final TicketService ticketService;
    private final CoreConfig config;

    public MaintenanceScheduler(ReportService reportService, TicketService ticketService, CoreConfig config) {
        this.reportService = reportService;
        this.ticketService = ticketService;
        this.config = config;
    }

    @Scheduled(fixedRate = 3_600_000, initialDelay = 3_600_000)
    public void closeStale() {
        int reportHours = config.getMaintenance().getReportAutoCloseHours();
        int ticketHours = config.getMaintenance().getTicketAutoCloseHours();

        int reportsClosed = reportService.closeStale(Duration.ofHours(reportHours));
        int ticketsClosed = ticketService.closeStale(Duration.ofHours(ticketHours));

        if (reportsClosed > 0 || ticketsClosed > 0) {
            log.info("Auto-closed {} stale report(s) and {} stale ticket(s)", reportsClosed, ticketsClosed);
        }
    }
}
