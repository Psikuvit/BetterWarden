package me.psikuvit.betterWarden.core.api;

import me.psikuvit.betterWarden.core.model.Punishment;
import me.psikuvit.betterWarden.core.network.dto.IssuePunishmentRequest;
import me.psikuvit.betterWarden.core.network.dto.PunishmentDto;
import me.psikuvit.betterWarden.core.network.dto.RevokeRequest;
import me.psikuvit.betterWarden.core.repo.PunishmentRepository;
import me.psikuvit.betterWarden.core.service.PunishmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Guarded by NodeAuthInterceptor (see WebConfig - applies to /api/v1/**). This is what a CLIENT
 * mode backend talks to: bulk snapshot at startup, a single uuid's punishments on a push
 * notification, and issuing/revoking punishments a CLIENT-side command triggers.
 */
@RestController
@RequestMapping("/api/v1/punishments")
public class NodeApiController {

    private final PunishmentService punishmentService;
    private final PunishmentRepository punishments;

    public NodeApiController(PunishmentService punishmentService, PunishmentRepository punishments) {
        this.punishmentService = punishmentService;
        this.punishments = punishments;
    }

    @GetMapping("/active")
    public List<PunishmentDto> active() {
        return punishments.findByActiveTrue().stream().map(PunishmentDto::from).toList();
    }

    @GetMapping("/by-uuid/{uuid}")
    public List<PunishmentDto> byUuid(@PathVariable String uuid) {
        return punishmentService.activePunishments(UUID.fromString(uuid)).stream().map(PunishmentDto::from).toList();
    }

    @PostMapping
    public PunishmentDto issue(@RequestBody IssuePunishmentRequest req) {
        Duration duration = req.durationSeconds() == null ? null : Duration.ofSeconds(req.durationSeconds());
        UUID staffUuid = req.staffUuid() == null ? null : UUID.fromString(req.staffUuid());
        Punishment punishment = punishmentService.issue(UUID.fromString(req.targetUuid()), req.targetName(),
                req.type(), req.reason(), staffUuid, duration, req.silent(), null);
        return PunishmentDto.from(punishment);
    }

    @PostMapping("/{id}/revoke")
    public ResponseEntity<PunishmentDto> revoke(@PathVariable Long id, @RequestBody RevokeRequest req) {
        UUID staffUuid = req.staffUuid() == null ? null : UUID.fromString(req.staffUuid());
        return punishmentService.revoke(id, staffUuid, req.reason())
                .map(p -> ResponseEntity.ok(PunishmentDto.from(p)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
