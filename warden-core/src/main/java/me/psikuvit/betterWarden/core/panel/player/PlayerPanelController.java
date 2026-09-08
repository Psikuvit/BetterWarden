package me.psikuvit.betterWarden.core.panel.player;

import me.psikuvit.betterWarden.core.model.NameHistoryEntry;
import me.psikuvit.betterWarden.core.model.Player;
import me.psikuvit.betterWarden.core.model.Punishment;
import me.psikuvit.betterWarden.core.model.PunishmentType;
import me.psikuvit.betterWarden.core.model.StaffNote;
import me.psikuvit.betterWarden.core.repo.IpHistoryRepository;
import me.psikuvit.betterWarden.core.repo.NameHistoryRepository;
import me.psikuvit.betterWarden.core.repo.PlayerRepository;
import me.psikuvit.betterWarden.core.service.AltDetectionService;
import me.psikuvit.betterWarden.core.service.PunishmentService;
import me.psikuvit.betterWarden.core.service.StaffNoteService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** docs/spec/04-PANEL.txt §1 /players, /players/{uuid}, §4 PLAYER PROFILE. */
@RestController
@RequestMapping("/api/panel/players")
public class PlayerPanelController {

    public record SearchResult(String uuid, String name, Instant lastSeen) {
    }

    public record PunishmentEntry(Long id, String type, String reason, String staffName, Instant issuedAt,
                                   Instant expiresAt, boolean active) {
    }

    public record AltEntry(String uuid, String name) {
    }

    public record NoteEntry(Long id, String staffUuid, String body, Instant createdAt) {
    }

    /** ipHistory is null (not omitted - null, so the frontend can tell "hidden" from "empty") for anything below ADMIN, per spec §6 "Never expose raw IPs to non-ADMIN roles" - these are hashes, not raw IPs, but the same rule is applied here since a hash is still a correlation key. */
    public record Profile(String uuid, String name, List<String> nameHistory, Instant firstSeen, Instant lastSeen,
                           List<PunishmentEntry> punishments, List<AltEntry> alts, List<NoteEntry> notes,
                           List<String> ipHistory) {
    }

    public record AddNoteRequest(String body) {
    }

    public record PunishRequest(PunishmentType type, String reason, Long durationSeconds, boolean silent) {
    }

    private final PlayerRepository players;
    private final NameHistoryRepository nameHistory;
    private final IpHistoryRepository ipHistory;
    private final AltDetectionService altDetection;
    private final StaffNoteService staffNotes;
    private final PunishmentService punishmentService;

    public PlayerPanelController(PlayerRepository players, NameHistoryRepository nameHistory,
                                  IpHistoryRepository ipHistory, AltDetectionService altDetection,
                                  StaffNoteService staffNotes, PunishmentService punishmentService) {
        this.players = players;
        this.nameHistory = nameHistory;
        this.ipHistory = ipHistory;
        this.altDetection = altDetection;
        this.staffNotes = staffNotes;
        this.punishmentService = punishmentService;
    }

    @GetMapping
    @PreAuthorize("hasRole('MODERATOR')")
    public List<SearchResult> search(@RequestParam String q) {
        if (q.isBlank()) {
            return List.of();
        }
        // A bare UUID query is an exact lookup; anything else searches names.
        try {
            return players.findById(UUID.fromString(q).toString())
                    .map(p -> List.of(new SearchResult(p.getUuid(), p.getLastName(), p.getLastSeen())))
                    .orElse(List.of());
        } catch (IllegalArgumentException notAUuid) {
            return players.findByLastNameContainingIgnoreCaseOrderByLastSeenDesc(q, PageRequest.of(0, 30)).stream()
                    .map(p -> new SearchResult(p.getUuid(), p.getLastName(), p.getLastSeen()))
                    .toList();
        }
    }

    @GetMapping("/{uuid}")
    @PreAuthorize("hasRole('MODERATOR')")
    public ResponseEntity<Profile> profile(@PathVariable String uuid, Authentication authentication) {
        Optional<Player> found = players.findById(uuid);
        if (found.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Player player = found.get();
        UUID playerUuid = UUID.fromString(uuid);

        List<String> names = nameHistory.findByUuidOrderBySeenAtDesc(uuid).stream()
                .map(NameHistoryEntry::getName).distinct().toList();
        List<PunishmentEntry> punishments = punishmentService.history(playerUuid, 25).stream()
                .map(this::toPunishmentEntry).toList();
        List<AltEntry> alts = altDetection.findAlts(playerUuid).stream()
                .map(p -> new AltEntry(p.getUuid(), p.getLastName())).toList();
        List<NoteEntry> notes = staffNotes.list(playerUuid).stream()
                .map(n -> new NoteEntry(n.getId(), n.getStaffUuid(), n.getBody(), n.getCreatedAt())).toList();
        List<String> ips = hasAdminRole(authentication)
                ? ipHistory.findByUuid(uuid).stream().map(e -> e.getIpHash() + " (" + e.getFirstSeen() + " - " + e.getLastSeen() + ")").toList()
                : null;

        return ResponseEntity.ok(new Profile(uuid, player.getLastName(), names, player.getFirstSeen(),
                player.getLastSeen(), punishments, alts, notes, ips));
    }

    @PostMapping("/{uuid}/notes")
    @PreAuthorize("hasRole('MODERATOR')")
    public NoteEntry addNote(@PathVariable String uuid, @RequestBody AddNoteRequest req) {
        // No panel-to-Minecraft account linking yet (spec §3) - notes from the panel are attributed to CONSOLE.
        StaffNote note = staffNotes.add(UUID.fromString(uuid), null, req.body()).orElseThrow();
        return new NoteEntry(note.getId(), note.getStaffUuid(), note.getBody(), note.getCreatedAt());
    }

    @PostMapping("/{uuid}/punish")
    @PreAuthorize("hasRole('MODERATOR')")
    public PunishmentEntry punish(@PathVariable String uuid, @RequestBody PunishRequest req) {
        Player player = players.findById(uuid).orElseThrow();
        Duration duration = req.durationSeconds() == null ? null : Duration.ofSeconds(req.durationSeconds());
        Punishment p = punishmentService.issue(UUID.fromString(uuid), player.getLastName(), req.type(), req.reason(),
                null, duration, req.silent(), null);
        return toPunishmentEntry(p);
    }

    private PunishmentEntry toPunishmentEntry(Punishment p) {
        return new PunishmentEntry(p.getId(), p.getType().name(), p.getReason(),
                punishmentService.resolveStaffName(p), p.getIssuedAt(), p.getExpiresAt(), p.isActive());
    }

    private boolean hasAdminRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> Objects.equals(a.getAuthority(), "ROLE_ADMIN") || a.getAuthority().equals("ROLE_OWNER"));
    }
}
