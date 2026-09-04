package me.psikuvit.betterWarden.core.network.dto;

import me.psikuvit.betterWarden.core.model.PunishmentType;

/** durationSeconds null = permanent. staffUuid null = console/unknown staff. */
public record IssuePunishmentRequest(String targetUuid, String targetName, PunishmentType type, String reason,
                                      String staffUuid, Long durationSeconds, boolean silent) {
}
