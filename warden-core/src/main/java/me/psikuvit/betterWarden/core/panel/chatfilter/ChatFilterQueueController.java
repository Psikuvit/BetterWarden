package me.psikuvit.betterWarden.core.panel.chatfilter;

import me.psikuvit.betterWarden.core.model.FilteredMessage;
import me.psikuvit.betterWarden.core.repo.FilteredMessageRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Review queue for ChatFilterService's FLAGGED/BLOCKED hits (docs/spec/04-PANEL.txt §4 SETTINGS
 * mentions a rule editor + live tester - not built, this is just the queue itself). MODERATOR+
 * only: this is the first panel endpoint with a real per-role check, not just "authenticated".
 */
@RestController
@RequestMapping("/api/panel/chat-filter")
public class ChatFilterQueueController {

    public record QueueEntry(Long id, String playerUuid, String playerName, String server,
                              String rawMessage, String matchedRule, String action, Instant createdAt) {
        static QueueEntry of(FilteredMessage m) {
            return new QueueEntry(m.getId(), m.getPlayerUuid(), m.getPlayerName(), m.getServer(),
                    m.getRawMessage(), m.getMatchedRule(), m.getAction().name(), m.getCreatedAt());
        }
    }

    private final FilteredMessageRepository filteredMessages;

    public ChatFilterQueueController(FilteredMessageRepository filteredMessages) {
        this.filteredMessages = filteredMessages;
    }

    @GetMapping("/queue")
    @PreAuthorize("hasRole('MODERATOR')")
    public List<QueueEntry> queue() {
        return filteredMessages.findByReviewedFalseOrderByCreatedAtDesc().stream()
                .map(QueueEntry::of)
                .toList();
    }

    @PostMapping("/queue/{id}/review")
    @PreAuthorize("hasRole('MODERATOR')")
    public ResponseEntity<Void> review(@PathVariable Long id, Authentication authentication) {
        return filteredMessages.findById(id).map(m -> {
            m.setReviewed(true);
            m.setReviewedBy(authentication.getName());
            filteredMessages.save(m);
            return ResponseEntity.noContent().<Void>build();
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }
}
