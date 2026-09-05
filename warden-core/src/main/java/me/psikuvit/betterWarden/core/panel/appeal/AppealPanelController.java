package me.psikuvit.betterWarden.core.panel.appeal;

import me.psikuvit.betterWarden.core.model.Appeal;
import me.psikuvit.betterWarden.core.model.AppealStatus;
import me.psikuvit.betterWarden.core.model.Player;
import me.psikuvit.betterWarden.core.model.Punishment;
import me.psikuvit.betterWarden.core.repo.PlayerRepository;
import me.psikuvit.betterWarden.core.repo.PunishmentRepository;
import me.psikuvit.betterWarden.core.service.AppealService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/** docs/spec/04-PANEL.txt §1 /appeals, §4 APPEALS - see Appeal.java for what's not built yet (the public submission form). */
@RestController
@RequestMapping("/api/panel/appeals")
public class AppealPanelController {

    public record Entry(Long id, String playerUuid, String playerName, Long punishmentId, String punishmentType,
                         String punishmentReason, String message, String status, String reviewedBy,
                         String reviewNote, Instant createdAt) {
    }

    public record ReviewRequest(String note) {
    }

    private final AppealService appeals;
    private final PlayerRepository players;
    private final PunishmentRepository punishments;

    public AppealPanelController(AppealService appeals, PlayerRepository players, PunishmentRepository punishments) {
        this.appeals = appeals;
        this.players = players;
        this.punishments = punishments;
    }

    @GetMapping
    @PreAuthorize("hasRole('MODERATOR')")
    public List<Entry> list(@RequestParam(required = false) AppealStatus status) {
        return appeals.list(status).stream().map(this::toEntry).toList();
    }

    /** No panel-to-Minecraft account linking yet (spec §3) - reviews from the panel are attributed to CONSOLE. */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('MODERATOR')")
    public ResponseEntity<Entry> approve(@PathVariable Long id, @RequestBody ReviewRequest req) {
        return appeals.approve(id, null, req.note()).map(a -> ResponseEntity.ok(toEntry(a))).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/deny")
    @PreAuthorize("hasRole('MODERATOR')")
    public ResponseEntity<Entry> deny(@PathVariable Long id, @RequestBody ReviewRequest req) {
        return appeals.deny(id, null, req.note()).map(a -> ResponseEntity.ok(toEntry(a))).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/more-info")
    @PreAuthorize("hasRole('MODERATOR')")
    public ResponseEntity<Entry> moreInfo(@PathVariable Long id, @RequestBody ReviewRequest req) {
        return appeals.requestMoreInfo(id, null, req.note()).map(a -> ResponseEntity.ok(toEntry(a))).orElseGet(() -> ResponseEntity.notFound().build());
    }

    private Entry toEntry(Appeal a) {
        String playerName = players.findById(a.getUuid()).map(Player::getLastName).orElse(a.getUuid());
        Punishment punishment = punishments.findById(a.getPunishmentId()).orElse(null);
        String type = punishment == null ? "?" : punishment.getType().name();
        String reason = punishment == null ? "(punishment not found)" : punishment.getReason();
        return new Entry(a.getId(), a.getUuid(), playerName, a.getPunishmentId(), type, reason, a.getMessage(),
                a.getStatus().name(), a.getReviewedBy(), a.getReviewNote(), a.getCreatedAt());
    }
}
