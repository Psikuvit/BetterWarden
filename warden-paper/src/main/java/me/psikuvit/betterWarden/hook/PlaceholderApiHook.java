package me.psikuvit.betterWarden.hook;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.psikuvit.betterWarden.core.service.PunishmentService;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * %warden_punishments%, %warden_muted%, %warden_staff_rank%. Only constructed after confirming
 * PlaceholderAPI is present (see BetterWarden.onEnable) - same lazy-linking reasoning as
 * LuckPermsHook.
 * <p>
 * %warden_active_reports% and %warden_open_tickets% from the spec aren't implemented - there's
 * no ReportService/TicketService yet.
 */
public class PlaceholderApiHook extends PlaceholderExpansion {

    private final PunishmentService punishmentService;
    private final LuckPermsHook luckPermsHook;

    public PlaceholderApiHook(PunishmentService punishmentService, LuckPermsHook luckPermsHook) {
        this.punishmentService = punishmentService;
        this.luckPermsHook = luckPermsHook;
    }

    @Override
    public @NonNull String getIdentifier() {
        return "warden";
    }

    @Override
    public @NonNull String getAuthor() {
        return "psikuvit";
    }

    @Override
    public @NonNull String getVersion() {
        return "1.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NonNull String params) {
        if (player == null) {
            return "";
        }
        return switch (params) {
            case "punishments" -> String.valueOf(punishmentService.activePunishments(player.getUniqueId()).size());
            case "muted" -> String.valueOf(punishmentService.activeMute(player.getUniqueId()).isPresent());
            case "staff_rank" -> resolveStaffRank(player);
            default -> null;
        };
    }

    private String resolveStaffRank(OfflinePlayer player) {
        Player online = player.getPlayer();
        if (luckPermsHook != null && online != null) {
            return luckPermsHook.primaryGroup(online).orElse("default");
        }
        return player.isOp() ? "op" : "default";
    }
}
