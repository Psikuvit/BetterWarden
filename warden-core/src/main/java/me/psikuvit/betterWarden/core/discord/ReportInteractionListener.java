package me.psikuvit.betterWarden.core.discord;

import me.psikuvit.betterWarden.core.config.CoreConfig;
import me.psikuvit.betterWarden.core.model.Player;
import me.psikuvit.betterWarden.core.model.Punishment;
import me.psikuvit.betterWarden.core.model.PunishmentType;
import me.psikuvit.betterWarden.core.model.Report;
import me.psikuvit.betterWarden.core.platform.PlatformBridge;
import me.psikuvit.betterWarden.core.service.PlayerTrackingService;
import me.psikuvit.betterWarden.core.service.PunishmentService;
import me.psikuvit.betterWarden.core.service.ReportService;
import me.psikuvit.betterWarden.core.util.DurationParser;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.modals.Modal;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * docs/spec/05-DISCORD-BOT.txt §3 - the Claim/Teleport/Punish/Dismiss buttons on a #reports
 * embed, and the Punish/Dismiss modals they open. Every action here (except Teleport, which
 * doesn't touch the report) goes through ReportService, which publishes ReportChangedEvent -
 * ReportFeedListener picks that up and keeps every future view of this embed in sync too, but
 * the interaction itself is acknowledged directly here so the clicking staff member sees the
 * update immediately rather than waiting on a second, separate edit.
 */
@Service
public class ReportInteractionListener extends ListenerAdapter {

    private final CoreConfig config;
    private final ReportService reportService;
    private final ReportEmbedBuilder embedBuilder;
    private final DiscordLinkService linkService;
    private final PunishmentService punishmentService;
    private final PlatformBridge bridge;
    private final PlayerTrackingService playerTracking;

    public ReportInteractionListener(CoreConfig config, ReportService reportService, ReportEmbedBuilder embedBuilder,
                                      DiscordLinkService linkService, PunishmentService punishmentService,
                                      PlatformBridge bridge, PlayerTrackingService playerTracking) {
        this.config = config;
        this.reportService = reportService;
        this.embedBuilder = embedBuilder;
        this.linkService = linkService;
        this.punishmentService = punishmentService;
        this.bridge = bridge;
        this.playerTracking = playerTracking;
    }

    @Override
    public void onButtonInteraction(@NonNull ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (event.getGuild() == null || event.getMember() == null || !id.startsWith("wreport:")) {
            return;
        }
        if (!DiscordPermissions.isStaff(config.getDiscord(), event.getMember())) {
            event.reply("You don't have permission to act on reports.").setEphemeral(true).queue();
            return;
        }
        if (id.startsWith(ReportEmbeds.CLAIM)) {
            claim(event, reportId(id, ReportEmbeds.CLAIM));
        } else if (id.startsWith(ReportEmbeds.TELEPORT)) {
            teleport(event, reportId(id, ReportEmbeds.TELEPORT));
        } else if (id.startsWith(ReportEmbeds.PUNISH)) {
            openPunishModal(event, reportId(id, ReportEmbeds.PUNISH));
        } else if (id.startsWith(ReportEmbeds.DISMISS)) {
            openDismissModal(event, reportId(id, ReportEmbeds.DISMISS));
        }
    }

    @Override
    public void onModalInteraction(@NonNull ModalInteractionEvent event) {
        String id = event.getModalId();
        if (event.getGuild() == null || event.getMember() == null || !id.startsWith("wreport:")) {
            return;
        }
        if (!DiscordPermissions.isStaff(config.getDiscord(), event.getMember())) {
            event.reply("You don't have permission to act on reports.").setEphemeral(true).queue();
            return;
        }
        if (id.startsWith(ReportEmbeds.PUNISH_MODAL)) {
            submitPunish(event, reportId(id, ReportEmbeds.PUNISH_MODAL));
        } else if (id.startsWith(ReportEmbeds.DISMISS_MODAL)) {
            submitDismiss(event, reportId(id, ReportEmbeds.DISMISS_MODAL));
        }
    }

    private void claim(ButtonInteractionEvent event, Long id) {
        UUID staffUuid = linkService.findLinkedUuid(event.getUser().getId()).orElse(null);
        Optional<Report> updated = reportService.claim(id, staffUuid);
        if (updated.isEmpty()) {
            event.reply("Report #" + id + " no longer exists.").setEphemeral(true).queue();
            return;
        }
        event.editMessageEmbeds(embedBuilder.embed(updated.get())).setComponents(ReportEmbeds.buttons(updated.get())).queue();
    }

    /** §3 "[Teleport] runs a tp for their linked MC account if online" - doesn't change the report, so no embed edit here. */
    private void teleport(ButtonInteractionEvent event, Long id) {
        Optional<UUID> staffUuid = linkService.findLinkedUuid(event.getUser().getId());
        if (staffUuid.isEmpty()) {
            event.reply("Link your Discord account first with `/link` (then `/link <code>` in-game).").setEphemeral(true).queue();
            return;
        }
        Optional<Player> staffPlayer = playerTracking.find(staffUuid.get());
        if (staffPlayer.isEmpty() || !bridge.isOnline(staffUuid.get())) {
            event.reply("Your linked Minecraft account isn't online right now.").setEphemeral(true).queue();
            return;
        }
        Optional<Report> report = reportService.find(id);
        if (report.isEmpty()) {
            event.reply("Report #" + id + " no longer exists.").setEphemeral(true).queue();
            return;
        }
        UUID targetUuid = UUID.fromString(report.get().getTarget());
        if (!bridge.isOnline(targetUuid)) {
            event.reply("The reported player is offline.").setEphemeral(true).queue();
            return;
        }
        String targetName = playerTracking.find(targetUuid).map(Player::getLastName).orElse(report.get().getTarget());
        bridge.runConsoleCommand("tp " + staffPlayer.get().getLastName() + " " + targetName);
        event.reply("Teleporting you to **" + targetName + "**.").setEphemeral(true).queue();
    }

    private void openPunishModal(ButtonInteractionEvent event, Long id) {
        TextInput type = TextInput.create("type", TextInputStyle.SHORT).setPlaceholder("ban").setRequired(true).build();
        TextInput duration = TextInput.create("duration", TextInputStyle.SHORT).setRequired(false).build();
        TextInput reason = TextInput.create("reason", TextInputStyle.PARAGRAPH).setRequired(true).build();
        event.replyModal(Modal.create(ReportEmbeds.PUNISH_MODAL + id, "Punish - report #" + id)
                .addComponents(Label.of("Type (ban / mute / kick / warn)", type),
                        Label.of("Duration (e.g. 1d, 12h - blank for permanent)", duration),
                        Label.of("Reason", reason))
                .build()).queue();
    }

    private void openDismissModal(ButtonInteractionEvent event, Long id) {
        TextInput reason = TextInput.create("reason", TextInputStyle.PARAGRAPH).setRequired(false).build();
        event.replyModal(Modal.create(ReportEmbeds.DISMISS_MODAL + id, "Dismiss - report #" + id)
                .addComponents(Label.of("Reason (optional)", reason))
                .build()).queue();
    }

    private void submitPunish(ModalInteractionEvent event, Long id) {
        Optional<Report> report = reportService.find(id);
        if (report.isEmpty()) {
            event.reply("Report #" + id + " no longer exists.").setEphemeral(true).queue();
            return;
        }
        String typeStr = value(event, "type");
        String durationStr = value(event, "duration");
        String reason = value(event, "reason");
        PunishmentType type;
        try {
            type = PunishmentType.valueOf(typeStr.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            event.reply("Unknown punishment type `" + typeStr + "` - use ban, mute, kick or warn.").setEphemeral(true).queue();
            return;
        }
        Duration duration = null;
        if (durationStr != null && !durationStr.isBlank()) {
            try {
                duration = DurationParser.parse(durationStr);
            } catch (IllegalArgumentException e) {
                event.reply("Invalid duration `" + durationStr + "`.").setEphemeral(true).queue();
                return;
            }
        }
        if (duration != null) {
            type = type == PunishmentType.BAN ? PunishmentType.TEMPBAN : type == PunishmentType.MUTE ? PunishmentType.TEMPMUTE : type;
        }

        UUID targetUuid = UUID.fromString(report.get().getTarget());
        String targetName = playerTracking.find(targetUuid).map(Player::getLastName).orElse(report.get().getTarget());
        UUID staffUuid = linkService.findLinkedUuid(event.getUser().getId()).orElse(null);

        Punishment punishment = punishmentService.issue(targetUuid, targetName, type, reason, staffUuid, duration, false, null);
        // Punishing the reported player resolves what the report was for - close it rather than leaving it open.
        Optional<Report> closed = reportService.close(id, staffUuid);

        event.deferEdit().queue();
        closed.ifPresent(r -> event.getHook().editOriginalEmbeds(embedBuilder.embed(r)).setComponents(ReportEmbeds.buttons(r)).queue());
        event.getHook().sendMessage("**" + punishment.getType() + "** issued to **" + targetName + "** (#" + punishment.getId()
                + ") by " + event.getUser().getAsTag() + ": " + reason).setEphemeral(true).queue();
    }

    private void submitDismiss(ModalInteractionEvent event, Long id) {
        String reason = value(event, "reason");
        UUID staffUuid = linkService.findLinkedUuid(event.getUser().getId()).orElse(null);
        Optional<Report> updated = reportService.dismiss(id, staffUuid, reason == null || reason.isBlank() ? null : reason);
        if (updated.isEmpty()) {
            event.reply("Report #" + id + " no longer exists.").setEphemeral(true).queue();
            return;
        }
        event.deferEdit().queue();
        event.getHook().editOriginalEmbeds(embedBuilder.embed(updated.get())).setComponents(ReportEmbeds.buttons(updated.get())).queue();
    }

    private static String value(ModalInteractionEvent event, String id) {
        var mapping = event.getValue(id);
        return mapping == null ? null : mapping.getAsString();
    }

    private static Long reportId(String componentId, String prefix) {
        return Long.parseLong(componentId.substring(prefix.length()));
    }
}
