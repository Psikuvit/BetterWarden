package me.psikuvit.betterWarden.core.event;

import me.psikuvit.betterWarden.core.model.Punishment;

/** Published alongside PunishmentChangedEvent - carries the full punishment for consumers that need more than "this UUID changed" (the Discord punishment-log feed). */
public record PunishmentIssuedEvent(Punishment punishment) {
}
