package me.psikuvit.betterWarden.core.network.dto;

/** Pushed over /ws/nodes on every PunishmentChangedEvent - a CLIENT node re-fetches that uuid's punishments via REST, it doesn't get the full payload here (keeps the wire format tiny and avoids a second serialization path to keep in sync). */
public record NodeEvent(String event, String uuid) {

    public static final String PUNISHMENT_CHANGED = "PUNISHMENT_CHANGED";

    public static NodeEvent punishmentChanged(String uuid) {
        return new NodeEvent(PUNISHMENT_CHANGED, uuid);
    }
}
