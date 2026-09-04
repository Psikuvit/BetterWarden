package me.psikuvit.betterWarden.core.network.dto;

import me.psikuvit.betterWarden.core.model.Punishment;
import me.psikuvit.betterWarden.core.model.PunishmentType;

import java.time.Instant;

/** Wire representation of a Punishment for the node REST API - a CLIENT node never sees the JPA entity directly. */
public record PunishmentDto(Long id, String uuid, PunishmentType type, String reason,
                             String staffUuid, Instant issuedAt, Instant expiresAt, boolean active,
                             boolean silent, String ipHash) {

    public static PunishmentDto from(Punishment p) {
        return new PunishmentDto(p.getId(), p.getUuid(), p.getType(), p.getReason(), p.getStaffUuid(),
                p.getIssuedAt(), p.getExpiresAt(), p.isActive(), p.isSilent(), p.getIpHash());
    }

    /** Detached instance for a CLIENT node's local cache - never persisted, just read. */
    public Punishment toEntity() {
        Punishment p = new Punishment();
        p.setId(id);
        p.setUuid(uuid);
        p.setType(type);
        p.setReason(reason);
        p.setStaffUuid(staffUuid);
        p.setIssuedAt(issuedAt);
        p.setExpiresAt(expiresAt);
        p.setActive(active);
        p.setSilent(silent);
        p.setIpHash(ipHash);
        return p;
    }
}
