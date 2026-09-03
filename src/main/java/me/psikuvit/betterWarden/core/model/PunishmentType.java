package me.psikuvit.betterWarden.core.model;

import java.util.EnumSet;
import java.util.Set;

public enum PunishmentType {
    BAN,
    TEMPBAN,
    IPBAN,
    MUTE,
    TEMPMUTE,
    WARN,
    KICK;

    public static final Set<PunishmentType> BAN_TYPES = EnumSet.of(BAN, TEMPBAN, IPBAN);
    public static final Set<PunishmentType> MUTE_TYPES = EnumSet.of(MUTE, TEMPMUTE);
}
