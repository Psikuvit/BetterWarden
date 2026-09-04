package me.psikuvit.betterWarden.core.discord;

import me.psikuvit.betterWarden.core.config.CoreConfig;
import me.psikuvit.betterWarden.core.event.EventBus;
import me.psikuvit.betterWarden.core.event.PunishmentIssuedEvent;
import me.psikuvit.betterWarden.core.event.PunishmentRevokedEvent;
import me.psikuvit.betterWarden.core.model.Player;
import me.psikuvit.betterWarden.core.model.Punishment;
import me.psikuvit.betterWarden.core.service.PlayerTrackingService;
import me.psikuvit.betterWarden.core.service.PunishmentService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.util.UUID;

/**
 * docs/spec/05-DISCORD-BOT.txt §4 #punishment-log - "every punishment as a compact embed",
 * "silent punishments excluded from public feeds". Only this one feed is wired up this pass -
 * #reports, #tickets, #appeals, #alerts, #node-status, #changelog are all still config-only
 * placeholders, see PLAN.md.
 */
@Component
public class PunishmentFeedListener {

    private final CoreConfig config;
    private final DiscordBotService bot;
    private final PunishmentService punishmentService;
    private final PlayerTrackingService playerTracking;

    public PunishmentFeedListener(EventBus eventBus, CoreConfig config, DiscordBotService bot,
                                   PunishmentService punishmentService, PlayerTrackingService playerTracking) {
        this.config = config;
        this.bot = bot;
        this.punishmentService = punishmentService;
        this.playerTracking = playerTracking;
        eventBus.subscribe(PunishmentIssuedEvent.class, e -> post(e.punishment(), false));
        eventBus.subscribe(PunishmentRevokedEvent.class, e -> post(e.punishment(), true));
    }

    private void post(Punishment punishment, boolean revoked) {
        if (punishment.isSilent()) {
            return;
        }
        TextChannel channel = bot.resolveChannel(config.getDiscord().getFeeds().getPunishmentLog());
        if (channel == null) {
            return;
        }
        String targetName = playerTracking.find(UUID.fromString(punishment.getUuid()))
                .map(Player::getLastName).orElse(punishment.getUuid());
        String staffName = punishmentService.resolveStaffName(punishment);

        EmbedBuilder embed = new EmbedBuilder()
                .setColor(revoked ? Color.GREEN : Color.RED)
                .setTitle((revoked ? "Revoked: " : "") + punishment.getType() + " - " + targetName)
                .addField("Reason", punishment.getReason(), false)
                .addField("Staff", staffName, true)
                .setFooter("#" + punishment.getId())
                .setTimestamp(java.time.Instant.now());
        channel.sendMessageEmbeds(embed.build()).queue();
    }
}
