package me.psikuvit.betterWarden.core.service;

import me.psikuvit.betterWarden.core.model.Punishment;

import java.util.Optional;
import java.util.UUID;

/**
 * What the gate listeners (ban/mute checks) actually need - implemented by PunishmentService
 * directly on a HOST core, and by a CLIENT's RemotePunishmentCache against a remote core, so the
 * same BanGateListener/MuteGateListener/MuteCommandBlockListener work unchanged in both modes.
 */
public interface PunishmentGateway {

    Optional<Punishment> activeBan(UUID uuid);

    Optional<Punishment> activeMute(UUID uuid);

    Optional<Punishment> activeIpBan(String ipHash);

    String resolveStaffName(Punishment punishment);
}
