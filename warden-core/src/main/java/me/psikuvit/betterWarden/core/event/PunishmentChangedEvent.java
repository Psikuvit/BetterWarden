package me.psikuvit.betterWarden.core.event;

/** Published by PunishmentService on issue/revoke so PunishmentCache can refresh just that player. */
public record PunishmentChangedEvent(String uuid) {
}
