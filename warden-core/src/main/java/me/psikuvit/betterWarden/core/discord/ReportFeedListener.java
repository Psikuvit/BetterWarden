package me.psikuvit.betterWarden.core.discord;

import me.psikuvit.betterWarden.core.config.CoreConfig;
import me.psikuvit.betterWarden.core.event.EventBus;
import me.psikuvit.betterWarden.core.event.ReportChangedEvent;
import me.psikuvit.betterWarden.core.event.ReportCreatedEvent;
import me.psikuvit.betterWarden.core.model.Report;
import me.psikuvit.betterWarden.core.service.ReportService;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.stereotype.Component;

/**
 * docs/spec/05-DISCORD-BOT.txt §3/§4 - posts the actionable report card to #reports on
 * ReportCreatedEvent, then keeps that same message in sync on ReportChangedEvent regardless of
 * which surface (in-game, panel, or a Discord button - see ReportInteractionListener) made the
 * change. Mirrors PunishmentFeedListener's shape.
 */
@Component
public class ReportFeedListener {

    private final CoreConfig config;
    private final DiscordBotService bot;
    private final ReportService reportService;
    private final ReportEmbedBuilder embedBuilder;

    public ReportFeedListener(EventBus eventBus, CoreConfig config, DiscordBotService bot, ReportService reportService,
                               ReportEmbedBuilder embedBuilder) {
        this.config = config;
        this.bot = bot;
        this.reportService = reportService;
        this.embedBuilder = embedBuilder;
        eventBus.subscribe(ReportCreatedEvent.class, e -> postNew(e.report()));
        eventBus.subscribe(ReportChangedEvent.class, e -> updateExisting(e.report()));
    }

    private void postNew(Report report) {
        TextChannel channel = bot.resolveChannel(config.getDiscord().getFeeds().getReports());
        if (channel == null) {
            return;
        }
        channel.sendMessageEmbeds(embedBuilder.embed(report))
                .setComponents(ReportEmbeds.buttons(report))
                .queue(message -> reportService.attachDiscordMessage(report.getId(), channel.getId(), message.getId()));
    }

    private void updateExisting(Report report) {
        if (report.getDiscordMessageId() == null) {
            return;
        }
        TextChannel channel = bot.resolveChannel(report.getDiscordChannelId());
        if (channel == null) {
            return;
        }
        channel.editMessageEmbedsById(report.getDiscordMessageId(), embedBuilder.embed(report))
                .setComponents(ReportEmbeds.buttons(report))
                .queue(msg -> {
                }, err -> {
                    // Message deleted/inaccessible - nothing more we can do, don't spam logs for it.
                });
    }
}
