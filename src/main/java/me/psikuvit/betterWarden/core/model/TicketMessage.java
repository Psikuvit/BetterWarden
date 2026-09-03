package me.psikuvit.betterWarden.core.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "ticket_msgs")
public class TicketMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(nullable = false, length = 36)
    private String author;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MessageSource source;

    @Lob
    @Column(nullable = false)
    private String body;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Column(nullable = false)
    private boolean internal;

    public TicketMessage() {
    }

    public Long getId() {
        return id;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public MessageSource getSource() {
        return source;
    }

    public void setSource(MessageSource source) {
        this.source = source;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public void setSentAt(Instant sentAt) {
        this.sentAt = sentAt;
    }

    public boolean isInternal() {
        return internal;
    }

    public void setInternal(boolean internal) {
        this.internal = internal;
    }
}
