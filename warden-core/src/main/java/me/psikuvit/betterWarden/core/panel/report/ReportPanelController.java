package me.psikuvit.betterWarden.core.panel.report;

import me.psikuvit.betterWarden.core.model.Player;
import me.psikuvit.betterWarden.core.model.Report;
import me.psikuvit.betterWarden.core.model.ReportStatus;
import me.psikuvit.betterWarden.core.repo.PlayerRepository;
import me.psikuvit.betterWarden.core.service.ReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * docs/spec/04-PANEL.txt §1 /reports, §4 REPORTS. Live updates (spec: "htmx/SSE - no refresh
 * needed") aren't built - this is a plain poll-on-load list for now. No "punish" action of its
 * own: the frontend links straight to the target's player profile, which already has one
 * (PlayerPanelController#punish) - a report is a pointer at a player, not a separate workflow.
 */
@RestController
@RequestMapping("/api/panel/reports")
public class ReportPanelController {

    public record Entry(Long id, String reporterUuid, String reporterName, String targetUuid, String targetName,
                         String reason, String category, String status, String claimedBy, String chatSnapshot,
                         String location, String server, Instant createdAt) {
    }

    private final ReportService reports;
    private final PlayerRepository players;

    public ReportPanelController(ReportService reports, PlayerRepository players) {
        this.reports = reports;
        this.players = players;
    }

    @GetMapping
    @PreAuthorize("hasRole('MODERATOR')")
    public List<Entry> list(@RequestParam(required = false) ReportStatus status) {
        return reports.list(status).stream().map(this::toEntry).toList();
    }

    @PostMapping("/{id}/claim")
    @PreAuthorize("hasRole('MODERATOR')")
    public ResponseEntity<Entry> claim(@PathVariable Long id) {
        // No panel-to-Minecraft account linking yet (spec §3) - claims/dismissals from the panel are attributed to CONSOLE.
        return reports.claim(id, null).map(r -> ResponseEntity.ok(toEntry(r))).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/dismiss")
    @PreAuthorize("hasRole('MODERATOR')")
    public ResponseEntity<Entry> dismiss(@PathVariable Long id) {
        return reports.dismiss(id, null).map(r -> ResponseEntity.ok(toEntry(r))).orElseGet(() -> ResponseEntity.notFound().build());
    }

    private Entry toEntry(Report r) {
        String reporterName = players.findById(r.getReporter()).map(Player::getLastName).orElse(r.getReporter());
        String targetName = players.findById(r.getTarget()).map(Player::getLastName).orElse(r.getTarget());
        return new Entry(r.getId(), r.getReporter(), reporterName, r.getTarget(), targetName, r.getReason(),
                r.getCategory(), r.getStatus().name(), r.getClaimedBy(), r.getChatSnapshot(), r.getLocation(),
                r.getServer(), r.getCreatedAt());
    }
}
