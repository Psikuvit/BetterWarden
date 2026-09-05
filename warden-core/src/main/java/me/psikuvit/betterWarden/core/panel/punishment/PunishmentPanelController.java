package me.psikuvit.betterWarden.core.panel.punishment;

import me.psikuvit.betterWarden.core.model.Player;
import me.psikuvit.betterWarden.core.model.Punishment;
import me.psikuvit.betterWarden.core.model.PunishmentType;
import me.psikuvit.betterWarden.core.repo.PlayerRepository;
import me.psikuvit.betterWarden.core.repo.PunishmentRepository;
import me.psikuvit.betterWarden.core.repo.PunishmentRevokeRepository;
import me.psikuvit.betterWarden.core.service.PunishmentService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** docs/spec/04-PANEL.txt §1 /punishments, /punishments/{id}, §4 PUNISHMENT BROWSER. */
@RestController
@RequestMapping("/api/panel/punishments")
public class PunishmentPanelController {

    public record Row(Long id, String playerUuid, String playerName, String type, String reason,
                       String staffUuid, String server, Instant issuedAt, Instant expiresAt, boolean active) {
    }

    public record BrowseResponse(List<Row> rows, long total, int page, int size) {
    }

    public record RevokeRequest(String reason) {
    }

    public record RevokeEvent(String staffUuid, String reason, Instant revokedAt) {
    }

    public record Detail(Long id, String playerUuid, String playerName, String type, String reason,
                          String staffUuid, String staffName, String server, Instant issuedAt, Instant expiresAt,
                          boolean active, boolean silent, boolean permanent, String ipHash, RevokeEvent revoke) {
    }

    private final PunishmentRepository punishments;
    private final PunishmentRevokeRepository revokes;
    private final PlayerRepository players;
    private final PunishmentService punishmentService;

    public PunishmentPanelController(PunishmentRepository punishments, PunishmentRevokeRepository revokes,
                                      PlayerRepository players, PunishmentService punishmentService) {
        this.punishments = punishments;
        this.revokes = revokes;
        this.players = players;
        this.punishmentService = punishmentService;
    }

    @GetMapping
    @PreAuthorize("hasRole('MODERATOR')")
    public BrowseResponse browse(@RequestParam(required = false) PunishmentType type,
                                  @RequestParam(required = false) String staff,
                                  @RequestParam(required = false) String server,
                                  @RequestParam(required = false) Boolean active,
                                  @RequestParam(required = false) Integer days,
                                  @RequestParam(required = false) String q,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "25") int size) {
        Instant since = days == null ? null : Instant.now().minus(Duration.ofDays(days));
        String search = (q == null || q.isBlank()) ? null : "%" + q.toLowerCase(Locale.ROOT) + "%";

        var result = punishments.search(type, staff, server, active, since, search,
                PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "issuedAt")));

        Map<String, String> names = nameLookup(result.getContent());
        List<Row> rows = result.getContent().stream().map(p -> toRow(p, names)).toList();
        return new BrowseResponse(rows, result.getTotalElements(), page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('MODERATOR')")
    public ResponseEntity<Detail> detail(@PathVariable Long id) {
        return punishments.findById(id).map(p -> {
            String playerName = players.findById(p.getUuid()).map(Player::getLastName).orElse(p.getUuid());
            RevokeEvent revoke = revokes.findById(id)
                    .map(r -> new RevokeEvent(r.getStaffUuid(), r.getReason(), r.getRevokedAt()))
                    .orElse(null);
            Detail detail = new Detail(p.getId(), p.getUuid(), playerName, p.getType().name(), p.getReason(),
                    p.getStaffUuid(), punishmentService.resolveStaffName(p), p.getServer(), p.getIssuedAt(),
                    p.getExpiresAt(), p.isActive(), p.isSilent(), p.isPermanent(), p.getIpHash(), revoke);
            return ResponseEntity.ok(detail);
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** No panel-to-Minecraft account linking yet (spec §3) - revokes from here are attributed to CONSOLE, same as the Discord bot's punishments (see ModerationSlashCommands). */
    @PostMapping("/{id}/revoke")
    @PreAuthorize("hasRole('MODERATOR')")
    public ResponseEntity<Void> revoke(@PathVariable Long id, @RequestBody RevokeRequest req) {
        return punishmentService.revoke(id, null, req.reason())
                .map(p -> ResponseEntity.noContent().<Void>build())
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private Map<String, String> nameLookup(List<Punishment> page) {
        List<String> uuids = page.stream().map(Punishment::getUuid).distinct().toList();
        Map<String, String> names = new HashMap<>();
        players.findAllById(uuids).forEach(pl -> names.put(pl.getUuid(), pl.getLastName()));
        return names;
    }

    private Row toRow(Punishment p, Map<String, String> names) {
        return new Row(p.getId(), p.getUuid(), names.getOrDefault(p.getUuid(), p.getUuid()), p.getType().name(),
                p.getReason(), p.getStaffUuid(), p.getServer(), p.getIssuedAt(), p.getExpiresAt(), p.isActive());
    }
}
