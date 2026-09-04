package me.psikuvit.betterWarden.core.event;

import me.psikuvit.betterWarden.core.model.Punishment;

/** Published alongside PunishmentChangedEvent on revoke - see PunishmentIssuedEvent. */
public record PunishmentRevokedEvent(Punishment punishment) {
}
