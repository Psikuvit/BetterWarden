package me.psikuvit.betterWarden.core.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "punish_revoke")
public class PunishmentRevoke {

    @Id
    @Column(name = "punishment_id")
    private Long punishmentId;

    @Column(name = "staff_uuid", nullable = false, length = 36)
    private String staffUuid;

    @Column(nullable = false, length = 512)
    private String reason;

    @Column(name = "revoked_at", nullable = false)
    private Instant revokedAt;

    public PunishmentRevoke() {
    }

    public Long getPunishmentId() {
        return punishmentId;
    }

    public void setPunishmentId(Long punishmentId) {
        this.punishmentId = punishmentId;
    }

    public String getStaffUuid() {
        return staffUuid;
    }

    public void setStaffUuid(String staffUuid) {
        this.staffUuid = staffUuid;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }
}
