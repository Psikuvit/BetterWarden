package me.psikuvit.betterWarden.core.discord;

import me.psikuvit.betterWarden.core.model.Report;
import me.psikuvit.betterWarden.core.model.ReportStatus;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * docs/spec/05-DISCORD-BOT.txt §3 "REPORT EMBEDS (the killer feature)" - builds the actionable
 * card and its buttons. Pure formatting only, no Spring/JDA network calls - ReportFeedListener
 * and ReportInteractionListener resolve names/counts first and hand them in.
 */
public final class ReportEmbeds {

    public static final String CLAIM = "wreport:claim:";
    public static final String TELEPORT = "wreport:teleport:";
    public static final String PUNISH = "wreport:punish:";
    public static final String DISMISS = "wreport:dismiss:";
    public static final String PUNISH_MODAL = "wreport:punishmodal:";
    public static final String DISMISS_MODAL = "wreport:dismissmodal:";

    private ReportEmbeds() {
    }

    public static MessageEmbed build(Report report, String reporterName, String targetName,
                                      int activePunishments, String claimedByName, String panelUrl) {
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(colorFor(report.getStatus()))
                .setTitle("Report #" + report.getId() + " - " + targetName)
                .setThumbnail("https://mc-heads.net/avatar/" + report.getTarget())
                .addField("Reporter", reporterName, true)
                .addField("Category", blankOr(report.getCategory(), "none"), true)
                .addField("Server", blankOr(report.getServer(), "unknown"), true)
                .addField("Reason", report.getReason(), false)
                .addField("Active punishments", String.valueOf(activePunishments), true)
                .addField("Status", report.getStatus().toString(), true);
        if (report.getLocation() != null && !report.getLocation().isBlank()) {
            embed.addField("Location", report.getLocation(), true);
        }
        String snapshot = report.getChatSnapshot();
        embed.addField("Recent chat (last 60s)",
                snapshot == null || snapshot.isBlank() ? "_(no recent chat captured)_" : "```" + truncate(snapshot, 900) + "```", false);
        if (claimedByName != null) {
            embed.addField("Claimed by", claimedByName, true);
        }
        if (panelUrl != null && !panelUrl.isBlank()) {
            embed.addField("Panel", "[Open the reports queue](" + panelUrl + "/reports)", false);
        }
        embed.setFooter("Report #" + report.getId()).setTimestamp(report.getCreatedAt());
        return embed.build();
    }

    public static List<ActionRow> buttons(Report report) {
        boolean terminal = report.getStatus() == ReportStatus.DISMISSED || report.getStatus() == ReportStatus.CLOSED;
        boolean claimed = report.getStatus() != ReportStatus.OPEN;
        List<Button> row = new ArrayList<>();
        row.add(Button.primary(CLAIM + report.getId(), "Claim").withDisabled(claimed || terminal));
        row.add(Button.secondary(TELEPORT + report.getId(), "Teleport").withDisabled(terminal));
        row.add(Button.success(PUNISH + report.getId(), "Punish").withDisabled(terminal));
        row.add(Button.danger(DISMISS + report.getId(), "Dismiss").withDisabled(terminal));
        return List.of(ActionRow.of(row));
    }

    private static Color colorFor(ReportStatus status) {
        return switch (status) {
            case OPEN -> Color.ORANGE;
            case CLAIMED -> Color.YELLOW;
            case DISMISSED -> Color.GRAY;
            case CLOSED -> Color.GREEN;
        };
    }

    private static String blankOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }
}
