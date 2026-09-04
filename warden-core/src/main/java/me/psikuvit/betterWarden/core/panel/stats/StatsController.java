package me.psikuvit.betterWarden.core.panel.stats;

import me.psikuvit.betterWarden.core.service.StatsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * docs/spec/04-PANEL.txt §4 STAFF STATS. `server` absent/blank is the page's "network
 * aggregation toggle" - one endpoint, not two, since that's how the spec's own page describes it
 * (a table with a toggle, not two separate views).
 */
@RestController
@RequestMapping("/api/panel/stats")
public class StatsController {

    private final StatsService stats;

    public StatsController(StatsService stats) {
        this.stats = stats;
    }

    @GetMapping("/staff")
    @PreAuthorize("hasRole('MODERATOR')")
    public List<StatsService.StaffStat> staff(@RequestParam(name = "days", defaultValue = "30") int days,
                                               @RequestParam(name = "server", required = false) String server) {
        return stats.perStaff(Instant.now().minus(Duration.ofDays(days)), server);
    }

    @GetMapping("/digest")
    @PreAuthorize("hasRole('MODERATOR')")
    public StatsService.WeeklyDigest digest() {
        return stats.weeklyDigest();
    }
}
