package me.psikuvit.betterWarden.core.panel.ticket;

import me.psikuvit.betterWarden.core.model.MessageSource;
import me.psikuvit.betterWarden.core.model.Player;
import me.psikuvit.betterWarden.core.model.Ticket;
import me.psikuvit.betterWarden.core.model.TicketMessage;
import me.psikuvit.betterWarden.core.model.TicketStatus;
import me.psikuvit.betterWarden.core.repo.PlayerRepository;
import me.psikuvit.betterWarden.core.service.TicketService;
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

/** docs/spec/04-PANEL.txt §1 /tickets, /tickets/{id}, §4 TICKETS. */
@RestController
@RequestMapping("/api/panel/tickets")
public class TicketPanelController {

    public record Entry(Long id, String subject, String openerUuid, String openerName, String status,
                         String priority, String assignee, String category, String source, Instant createdAt) {
    }

    public record Message(Long id, String author, String authorName, String source, String body,
                           boolean internal, Instant sentAt) {
    }

    public record Thread(Entry ticket, List<Message> messages) {
    }

    public record ReplyRequest(String body, boolean internal) {
    }

    private final TicketService tickets;
    private final PlayerRepository players;

    public TicketPanelController(TicketService tickets, PlayerRepository players) {
        this.tickets = tickets;
        this.players = players;
    }

    @GetMapping
    @PreAuthorize("hasRole('MODERATOR')")
    public List<Entry> list(@RequestParam(required = false) TicketStatus status) {
        return tickets.list(status).stream().map(this::toEntry).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('MODERATOR')")
    public ResponseEntity<Thread> thread(@PathVariable Long id) {
        return tickets.find(id).map(t -> {
            List<Message> messages = tickets.messages(id).stream().map(this::toMessage).toList();
            return ResponseEntity.ok(new Thread(toEntry(t), messages));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** No panel-to-Minecraft account linking yet (spec §3) - panel replies are attributed to CONSOLE, source WEB per spec §4 "source badges (game / Discord / web)". */
    @PostMapping("/{id}/reply")
    @PreAuthorize("hasRole('MODERATOR')")
    public ResponseEntity<Message> reply(@PathVariable Long id, @RequestBody ReplyRequest req) {
        return tickets.reply(id, null, req.body(), req.internal(), MessageSource.WEB)
                .map(m -> ResponseEntity.ok(toMessage(m)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasRole('MODERATOR')")
    public ResponseEntity<Entry> close(@PathVariable Long id) {
        return tickets.close(id, null).map(t -> ResponseEntity.ok(toEntry(t))).orElseGet(() -> ResponseEntity.notFound().build());
    }

    private Entry toEntry(Ticket t) {
        String openerName = players.findById(t.getOpener()).map(Player::getLastName).orElse(t.getOpener());
        // The ticket itself has no source field - source is per-message (spec's "game / Discord /
        // web" badges are really about how each message arrived); the opening message's source is
        // what the inbox list badge shows, since that's the closest the spec's own mockup implies.
        List<TicketMessage> messages = tickets.messages(t.getId());
        String source = messages.isEmpty() ? MessageSource.GAME.name() : messages.get(0).getSource().name();
        return new Entry(t.getId(), t.getSubject(), t.getOpener(), openerName, t.getStatus().name(),
                t.getPriority().name(), t.getAssignee(), t.getCategory(), source, t.getCreatedAt());
    }

    private Message toMessage(TicketMessage m) {
        String authorName = "CONSOLE".equals(m.getAuthor()) ? "CONSOLE"
                : players.findById(m.getAuthor()).map(Player::getLastName).orElse(m.getAuthor());
        return new Message(m.getId(), m.getAuthor(), authorName, m.getSource().name(), m.getBody(),
                m.isInternal(), m.getSentAt());
    }
}
