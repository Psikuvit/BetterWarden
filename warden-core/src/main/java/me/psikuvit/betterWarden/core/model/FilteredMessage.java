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
@Table(name = "filtered_messages")
public class FilteredMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "player_uuid", nullable = false, length = 36)
    private String playerUuid;

    @Column(name = "player_name", nullable = false, length = 16)
    private String playerName;

    @Column(length = 64)
    private String server;

    @Lob
    @Column(name = "raw_message", nullable = false)
    private String rawMessage;

    @Column(name = "matched_rule", nullable = false, length = 64)
    private String matchedRule;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FilterAction action;

    @Column(nullable = false)
    private boolean reviewed = false;

    @Column(name = "reviewed_by", length = 36)
    private String reviewedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public FilteredMessage() {
    }

    public Long getId() {
        return id;
    }

    public String getPlayerUuid() {
        return playerUuid;
    }

    public void setPlayerUuid(String playerUuid) {
        this.playerUuid = playerUuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public String getServer() {
        return server;
    }

    public void setServer(String server) {
        this.server = server;
    }

    public String getRawMessage() {
        return rawMessage;
    }

    public void setRawMessage(String rawMessage) {
        this.rawMessage = rawMessage;
    }

    public String getMatchedRule() {
        return matchedRule;
    }

    public void setMatchedRule(String matchedRule) {
        this.matchedRule = matchedRule;
    }

    public FilterAction getAction() {
        return action;
    }

    public void setAction(FilterAction action) {
        this.action = action;
    }

    public boolean isReviewed() {
        return reviewed;
    }

    public void setReviewed(boolean reviewed) {
        this.reviewed = reviewed;
    }

    public String getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(String reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
