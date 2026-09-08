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
@Table(name = "reports")
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 36)
    private String reporter;

    @Column(nullable = false, length = 36)
    private String target;

    @Column(nullable = false, length = 512)
    private String reason;

    @Column(length = 64)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReportStatus status;

    @Column(name = "claimed_by", length = 36)
    private String claimedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Lob
    @Column(name = "chat_snapshot")
    private String chatSnapshot;

    @Column(length = 128)
    private String location;

    @Column(length = 64)
    private String server;

    /** Set once the Discord #reports feed (docs/spec/05-DISCORD-BOT.txt §3/§4) has posted this report's actionable embed - lets later status changes, from either surface, edit that same message in place instead of spamming a new one. Null if the feed isn't configured. */
    @Column(name = "discord_channel_id", length = 32)
    private String discordChannelId;

    @Column(name = "discord_message_id", length = 32)
    private String discordMessageId;

    public Report() {
    }

    public Long getId() {
        return id;
    }

    public String getReporter() {
        return reporter;
    }

    public void setReporter(String reporter) {
        this.reporter = reporter;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public ReportStatus getStatus() {
        return status;
    }

    public void setStatus(ReportStatus status) {
        this.status = status;
    }

    public String getClaimedBy() {
        return claimedBy;
    }

    public void setClaimedBy(String claimedBy) {
        this.claimedBy = claimedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }

    public String getChatSnapshot() {
        return chatSnapshot;
    }

    public void setChatSnapshot(String chatSnapshot) {
        this.chatSnapshot = chatSnapshot;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getServer() {
        return server;
    }

    public void setServer(String server) {
        this.server = server;
    }

    public String getDiscordChannelId() {
        return discordChannelId;
    }

    public void setDiscordChannelId(String discordChannelId) {
        this.discordChannelId = discordChannelId;
    }

    public String getDiscordMessageId() {
        return discordMessageId;
    }

    public void setDiscordMessageId(String discordMessageId) {
        this.discordMessageId = discordMessageId;
    }
}
