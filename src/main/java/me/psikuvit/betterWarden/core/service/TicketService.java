package me.psikuvit.betterWarden.core.service;

import me.psikuvit.betterWarden.core.model.ActorType;
import me.psikuvit.betterWarden.core.model.AuditLogEntry;
import me.psikuvit.betterWarden.core.model.MessageSource;
import me.psikuvit.betterWarden.core.model.Ticket;
import me.psikuvit.betterWarden.core.model.TicketMessage;
import me.psikuvit.betterWarden.core.model.TicketPriority;
import me.psikuvit.betterWarden.core.model.TicketStatus;
import me.psikuvit.betterWarden.core.repo.AuditLogRepository;
import me.psikuvit.betterWarden.core.repo.TicketMessageRepository;
import me.psikuvit.betterWarden.core.repo.TicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class TicketService {

    private final TicketRepository tickets;
    private final TicketMessageRepository messages;
    private final AuditLogRepository auditLog;

    public TicketService(TicketRepository tickets, TicketMessageRepository messages, AuditLogRepository auditLog) {
        this.tickets = tickets;
        this.messages = messages;
        this.auditLog = auditLog;
    }

    @Transactional
    public Ticket open(UUID opener, String subject, String firstMessage, String category) {
        Ticket ticket = new Ticket();
        ticket.setOpener(opener.toString());
        ticket.setSubject(subject);
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setPriority(TicketPriority.NORMAL);
        ticket.setCategory(category);
        ticket.setCreatedAt(Instant.now());
        ticket = tickets.save(ticket);
        addMessage(ticket.getId(), opener, firstMessage, MessageSource.GAME, false);
        return ticket;
    }

    @Transactional
    public Optional<TicketMessage> reply(Long ticketId, UUID author, String body, boolean internal) {
        Optional<Ticket> found = tickets.findById(ticketId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Ticket ticket = found.get();
        if (ticket.getStatus() == TicketStatus.OPEN) {
            ticket.setStatus(TicketStatus.IN_PROGRESS);
        }
        return Optional.of(addMessage(ticketId, author, body, MessageSource.GAME, internal));
    }

    @Transactional
    public Optional<Ticket> assign(Long ticketId, UUID assignee) {
        Optional<Ticket> found = tickets.findById(ticketId);
        found.ifPresent(t -> {
            t.setAssignee(assignee == null ? null : assignee.toString());
            if (t.getStatus() == TicketStatus.OPEN) {
                t.setStatus(TicketStatus.IN_PROGRESS);
            }
            audit(assignee, "TICKET_ASSIGN", ticketId.toString());
        });
        return found;
    }

    @Transactional
    public Optional<Ticket> close(Long ticketId, UUID staffUuid) {
        Optional<Ticket> found = tickets.findById(ticketId);
        found.ifPresent(t -> {
            t.setStatus(TicketStatus.CLOSED);
            t.setClosedAt(Instant.now());
            audit(staffUuid, "TICKET_CLOSE", ticketId.toString());
        });
        return found;
    }

    public List<Ticket> list(TicketStatus status) {
        return status == null ? tickets.findAllByOrderByCreatedAtDesc() : tickets.findByStatusOrderByCreatedAtDesc(status);
    }

    public List<Ticket> assignedTo(UUID staff) {
        return tickets.findByAssigneeAndStatusNotOrderByCreatedAtDesc(staff.toString(), TicketStatus.CLOSED);
    }

    public Optional<Ticket> find(Long id) {
        return tickets.findById(id);
    }

    public List<TicketMessage> messages(Long ticketId) {
        return messages.findByTicketIdOrderBySentAtAsc(ticketId);
    }

    private TicketMessage addMessage(Long ticketId, UUID author, String body, MessageSource source, boolean internal) {
        TicketMessage message = new TicketMessage();
        message.setTicketId(ticketId);
        message.setAuthor(author == null ? "CONSOLE" : author.toString());
        message.setSource(source);
        message.setBody(body);
        message.setSentAt(Instant.now());
        message.setInternal(internal);
        return messages.save(message);
    }

    private void audit(UUID staffUuid, String action, String target) {
        AuditLogEntry entry = new AuditLogEntry();
        entry.setActor(staffUuid == null ? "CONSOLE" : staffUuid.toString());
        entry.setActorType(staffUuid == null ? ActorType.CONSOLE : ActorType.STAFF);
        entry.setAction(action);
        entry.setTarget(target);
        entry.setAt(Instant.now());
        auditLog.save(entry);
    }
}
