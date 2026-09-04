package me.psikuvit.betterWarden.core.discord;

import me.psikuvit.betterWarden.core.config.CoreConfig;
import me.psikuvit.betterWarden.core.model.Player;
import me.psikuvit.betterWarden.core.service.PlayerTrackingService;
import me.psikuvit.betterWarden.core.service.StatsService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * docs/spec/05-DISCORD-BOT.txt §7 - reuses StatsService.weeklyDigest(), which already existed
 * from Stage 5's stats work (this is the first thing that actually delivers it anywhere).
 * Fixed schedule for MVP - spec's "configurable day/time and timezone" isn't built, see PLAN.md.
 */
@Component
public class WeeklyDigestScheduler {

    private static final Logger log = LoggerFactory.getLogger(WeeklyDigestScheduler.class);

    private final CoreConfig config;
    private final DiscordBotService bot;
    private final StatsService stats;
    private final PlayerTrackingService playerTracking;

    public WeeklyDigestScheduler(CoreConfig config, DiscordBotService bot, StatsService stats, PlayerTrackingService playerTracking) {
        this.config = config;
        this.bot = bot;
        this.stats = stats;
        this.playerTracking = playerTracking;
    }

    /** Every Monday 09:00, server-local time. */
    @Scheduled(cron = "0 0 9 * * MON")
    public void postDigest() {
        TextChannel channel = bot.resolveChannel(config.getDiscord().getFeeds().getDigest());
        if (channel == null) {
            return;
        }
        StatsService.WeeklyDigest digest = stats.weeklyDigest();

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Weekly Digest")
                .addField("Punishments issued", String.valueOf(digest.totalPunishments()), true)
                .addField("Reports handled", String.valueOf(digest.totalReports()), true)
                .addField("Tickets closed", String.valueOf(digest.totalTickets()), true);

        if (digest.topStaff().isEmpty()) {
            embed.addField("Top staff", "No staff activity this week.", false);
        } else {
            StringBuilder top = new StringBuilder();
            for (StatsService.StaffStat s : digest.topStaff()) {
                String name = playerTracking.find(UUID.fromString(s.staffUuid())).map(Player::getLastName).orElse(s.staffUuid());
                top.append(name).append(": ").append(s.punishmentsIssued()).append(" punishment(s)\n");
            }
            embed.addField("Top staff", top.toString(), false);
        }
        embed.setTimestamp(digest.periodEnd());
        channel.sendMessageEmbeds(embed.build()).queue();
        log.info("Posted weekly Discord digest ({} punishment(s), {} report(s), {} ticket(s))",
                digest.totalPunishments(), digest.totalReports(), digest.totalTickets());
    }
}
