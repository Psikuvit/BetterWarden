package me.psikuvit.betterWarden.core.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "punish_template")
public class PunishmentTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_key", nullable = false, unique = true, length = 64)
    private String key;

    @Column(nullable = false, length = 128)
    private String display;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PunishmentType type;

    @Column(length = 32)
    private String duration;

    @Column(nullable = false, length = 256)
    private String reason;

    @Column(name = "escalation_group", length = 64)
    private String escalationGroup;

    public PunishmentTemplate() {
    }

    public Long getId() {
        return id;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getDisplay() {
        return display;
    }

    public void setDisplay(String display) {
        this.display = display;
    }

    public PunishmentType getType() {
        return type;
    }

    public void setType(PunishmentType type) {
        this.type = type;
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getEscalationGroup() {
        return escalationGroup;
    }

    public void setEscalationGroup(String escalationGroup) {
        this.escalationGroup = escalationGroup;
    }
}
