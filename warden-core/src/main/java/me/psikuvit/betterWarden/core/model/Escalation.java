package me.psikuvit.betterWarden.core.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "escalations", uniqueConstraints = @UniqueConstraint(columnNames = {"group_key", "offence_number"}))
public class Escalation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_key", nullable = false, length = 64)
    private String groupKey;

    @Column(name = "offence_number", nullable = false)
    private int offenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PunishmentType type;

    @Column(length = 32)
    private String duration;

    public Escalation() {
    }

    public Long getId() {
        return id;
    }

    public String getGroupKey() {
        return groupKey;
    }

    public void setGroupKey(String groupKey) {
        this.groupKey = groupKey;
    }

    public int getOffenceNumber() {
        return offenceNumber;
    }

    public void setOffenceNumber(int offenceNumber) {
        this.offenceNumber = offenceNumber;
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
}
