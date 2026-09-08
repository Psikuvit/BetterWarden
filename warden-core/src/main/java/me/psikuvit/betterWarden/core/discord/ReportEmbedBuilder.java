package me.psikuvit.betterWarden.core.discord;

import me.psikuvit.betterWarden.core.config.CoreConfig;
import me.psikuvit.betterWarden.core.model.Player;
import me.psikuvit.betterWarden.core.model.Report;
import me.psikuvit.betterWarden.core.service.PlayerTrackingService;
import me.psikuvit.betterWarden.core.service.PunishmentService;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Resolves the names/counts a report embed needs and hands them to ReportEmbeds.build(). Its own
 * component so both ReportFeedListener (depends on DiscordBotService) and
 * ReportInteractionListener (registered ON DiscordBotService) can use it without either depending
 * on the other - that would be a circular Spring bean graph, which Boot 4 doesn't auto-resolve.
 */
@Component
public class ReportEmbedBuilder {

    private final CoreConfig config;
    private final PunishmentService punishmentService;
    private final PlayerTrackingService playerTracking;

    public ReportEmbedBuilder(CoreConfig config, PunishmentService punishmentService, PlayerTrackingService playerTracking) {
        this.config = config;
        this.punishmentService = punishmentService;
        this.playerTracking = playerTracking;
    }

    public MessageEmbed embed(Report report) {
        String reporterName = name(report.getReporter());
        String targetName = name(report.getTarget());
        int activeCount = punishmentService.activePunishments(UUID.fromString(report.getTarget())).size();
        String claimedByName = report.getClaimedBy() == null ? null : name(report.getClaimedBy());
        String panelUrl = config.getPanel().getPublicUrl();
        return ReportEmbeds.build(report, reporterName, targetName, activeCount, claimedByName, panelUrl.isBlank() ? null : panelUrl);
    }

    private String name(String uuidString) {
        try {
            return playerTracking.find(UUID.fromString(uuidString)).map(Player::getLastName).orElse(uuidString);
        } catch (IllegalArgumentException e) {
            return uuidString;
        }
    }
}
