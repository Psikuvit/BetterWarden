package me.psikuvit.betterWarden.core.discord;

import me.psikuvit.betterWarden.core.config.CoreConfig;
import me.psikuvit.betterWarden.core.model.Player;
import me.psikuvit.betterWarden.core.model.Punishment;
import me.psikuvit.betterWarden.core.model.PunishmentType;
import me.psikuvit.betterWarden.core.platform.PlatformBridge;
import me.psikuvit.betterWarden.core.repo.PlayerRepository;
import me.psikuvit.betterWarden.core.service.AltDetectionService;
import me.psikuvit.betterWarden.core.service.MojangApiService;
import me.psikuvit.betterWarden.core.service.PlayerTrackingService;
import me.psikuvit.betterWarden.core.service.PunishmentService;
import me.psikuvit.betterWarden.core.service.StaffNoteService;
import me.psikuvit.betterWarden.core.util.DurationParser;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * docs/spec/05-DISCORD-BOT.txt §2 - core moderation commands: /ban /unban /mute /unmute /kick
 * /warn /history /lookup /note, plus §6 account linking (/link /unlink). Deferred (see PLAN.md):
 * /reports /tickets /appeals /status /staffstats /whois, and autocomplete on player names.
 *
 * Discord attribution is a plain UUID field just like every other punishment source. When the
 * acting Discord user has linked their Minecraft account (§6), that UUID is used as staffUuid;
 * otherwise it falls back to null, same as the chat filter's auto-mute. The embed/reply always
 * names the acting Discord user for an audit trail regardless of link status.
 */
@Service
public class ModerationSlashCommands extends ListenerAdapter {

    private final CoreConfig config;
    private final PunishmentService punishmentService;
    private final PlayerTrackingService playerTracking;
    private final StaffNoteService noteService;
    private final AltDetectionService altDetectionService;
    private final PlatformBridge bridge;
    private final PlayerRepository players;
    private final MojangApiService mojangApi;
    private final DiscordLinkService linkService;

    public ModerationSlashCommands(CoreConfig config, PunishmentService punishmentService, PlayerTrackingService playerTracking,
                                    StaffNoteService noteService, AltDetectionService altDetectionService,
                                    PlatformBridge bridge, PlayerRepository players, MojangApiService mojangApi,
                                    DiscordLinkService linkService) {
        this.config = config;
        this.punishmentService = punishmentService;
        this.playerTracking = playerTracking;
        this.noteService = noteService;
        this.altDetectionService = altDetectionService;
        this.bridge = bridge;
        this.players = players;
        this.mojangApi = mojangApi;
        this.linkService = linkService;
    }

    /** Guild-scoped registration (instant) rather than global (up to an hour to propagate) - this bot only ever serves one guild anyway. */
    public void registerCommands(Guild guild) {
        List<CommandData> commands = List.of(
                Commands.slash("ban", "Ban a player")
                        .addOption(OptionType.STRING, "player", "Player name", true)
                        .addOption(OptionType.STRING, "reason", "Reason", true)
                        .addOption(OptionType.STRING, "duration", "e.g. 1d, 12h - omit for permanent", false),
                Commands.slash("unban", "Remove an active ban")
                        .addOption(OptionType.STRING, "player", "Player name", true)
                        .addOption(OptionType.STRING, "reason", "Reason", true),
                Commands.slash("mute", "Mute a player")
                        .addOption(OptionType.STRING, "player", "Player name", true)
                        .addOption(OptionType.STRING, "reason", "Reason", true)
                        .addOption(OptionType.STRING, "duration", "e.g. 1d, 12h - omit for permanent", false),
                Commands.slash("unmute", "Remove an active mute")
                        .addOption(OptionType.STRING, "player", "Player name", true)
                        .addOption(OptionType.STRING, "reason", "Reason", true),
                Commands.slash("kick", "Kick a player")
                        .addOption(OptionType.STRING, "player", "Player name", true)
                        .addOption(OptionType.STRING, "reason", "Reason", true),
                Commands.slash("warn", "Warn a player")
                        .addOption(OptionType.STRING, "player", "Player name", true)
                        .addOption(OptionType.STRING, "reason", "Reason", true),
                Commands.slash("history", "A player's punishment history")
                        .addOption(OptionType.STRING, "player", "Player name", true),
                Commands.slash("lookup", "A player's profile summary")
                        .addOption(OptionType.STRING, "player", "Player name", true),
                Commands.slash("note", "Add a staff note to a player")
                        .addOption(OptionType.STRING, "player", "Player name", true)
                        .addOption(OptionType.STRING, "text", "Note text", true),
                Commands.slash("link", "Link your Discord account to your Minecraft account"),
                Commands.slash("unlink", "Remove your Discord-Minecraft account link")
        );
        guild.updateCommands().addCommands(commands).queue();
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null || event.getMember() == null) {
            return;
        }
        // Open to every member, not staff-gated below - linking your own account isn't a moderation action.
        if ("link".equals(event.getName())) {
            link(event);
            return;
        }
        if ("unlink".equals(event.getName())) {
            unlink(event);
            return;
        }
        if (!DiscordPermissions.isStaff(config.getDiscord(), event.getMember())) {
            event.reply("You don't have permission to use BetterWarden commands.").setEphemeral(true).queue();
            return;
        }
        event.deferReply().queue();

        String name = event.getOption("player", OptionMapping::getAsString);
        switch (event.getName()) {
            case "ban" -> punish(event, name, PunishmentType.BAN);
            case "mute" -> punish(event, name, PunishmentType.MUTE);
            case "kick" -> punish(event, name, PunishmentType.KICK);
            case "warn" -> punish(event, name, PunishmentType.WARN);
            case "unban" -> unpunish(event, name, Set.of(PunishmentType.BAN, PunishmentType.TEMPBAN, PunishmentType.IPBAN));
            case "unmute" -> unpunish(event, name, Set.of(PunishmentType.MUTE, PunishmentType.TEMPMUTE));
            case "history" -> history(event, name);
            case "lookup" -> lookup(event, name);
            case "note" -> note(event, name, event.getOption("text", OptionMapping::getAsString));
        }
    }

    private void punish(SlashCommandInteractionEvent event, String name, PunishmentType baseType) {
        String reason = event.getOption("reason", OptionMapping::getAsString);
        String durationStr = event.getOption("duration", OptionMapping::getAsString);
        Duration duration = null;
        if (durationStr != null && !durationStr.isBlank()) {
            try {
                duration = DurationParser.parse(durationStr);
            } catch (IllegalArgumentException e) {
                event.getHook().editOriginal("Invalid duration: `" + durationStr + "`").queue();
                return;
            }
        }
        PunishmentType type = duration == null ? baseType : (baseType == PunishmentType.BAN ? PunishmentType.TEMPBAN
                : baseType == PunishmentType.MUTE ? PunishmentType.TEMPMUTE : baseType);
        Duration finalDuration = duration;
        UUID staffUuid = linkService.findLinkedUuid(event.getUser().getId()).orElse(null);

        DiscordTargetResolver.resolve(bridge, players, mojangApi, name).thenAccept(target -> {
            if (target.isEmpty()) {
                event.getHook().editOriginal("Could not find a player named `" + name + "`.").queue();
                return;
            }
            if (PunishmentType.MUTE_TYPES.contains(type) && punishmentService.activeMute(target.get().uuid()).isPresent()) {
                event.getHook().editOriginal(target.get().name() + " is already muted.").queue();
                return;
            }
            Punishment punishment = punishmentService.issue(target.get().uuid(), target.get().name(), type, reason,
                    staffUuid, finalDuration, false, null);
            event.getHook().editOriginal("**" + punishment.getType() + "** issued to **" + target.get().name()
                    + "** (#" + punishment.getId() + ") by " + event.getUser().getAsTag() + ": " + reason).queue();
        });
    }

    private void unpunish(SlashCommandInteractionEvent event, String name, Set<PunishmentType> types) {
        String reason = event.getOption("reason", OptionMapping::getAsString);
        UUID staffUuid = linkService.findLinkedUuid(event.getUser().getId()).orElse(null);
        DiscordTargetResolver.resolve(bridge, players, mojangApi, name).thenAccept(target -> {
            if (target.isEmpty()) {
                event.getHook().editOriginal("Could not find a player named `" + name + "`.").queue();
                return;
            }
            List<Punishment> active = punishmentService.activePunishments(target.get().uuid()).stream()
                    .filter(p -> types.contains(p.getType()))
                    .toList();
            if (active.isEmpty()) {
                event.getHook().editOriginal(target.get().name() + " has no matching active punishment.").queue();
                return;
            }
            for (Punishment p : active) {
                punishmentService.revoke(p.getId(), staffUuid, reason);
            }
            event.getHook().editOriginal("Cleared " + active.size() + " punishment(s) for **" + target.get().name()
                    + "** by " + event.getUser().getAsTag() + ": " + reason).queue();
        });
    }

    private void history(SlashCommandInteractionEvent event, String name) {
        DiscordTargetResolver.resolve(bridge, players, mojangApi, name).thenAccept(target -> {
            if (target.isEmpty()) {
                event.getHook().editOriginal("Could not find a player named `" + name + "`.").queue();
                return;
            }
            List<Punishment> history = punishmentService.history(target.get().uuid(), 10);
            EmbedBuilder embed = new EmbedBuilder().setTitle("History: " + target.get().name());
            if (history.isEmpty()) {
                embed.setDescription("No punishment history.");
            } else {
                for (Punishment p : history) {
                    String status = p.isActive() ? "active" : "inactive";
                    embed.addField("#" + p.getId() + " " + p.getType() + " (" + status + ")", p.getReason(), false);
                }
            }
            event.getHook().editOriginalEmbeds(embed.build()).queue();
        });
    }

    private void lookup(SlashCommandInteractionEvent event, String name) {
        DiscordTargetResolver.resolve(bridge, players, mojangApi, name).thenAccept(target -> {
            if (target.isEmpty()) {
                event.getHook().editOriginal("Could not find a player named `" + name + "`.").queue();
                return;
            }
            UUID uuid = target.get().uuid();
            Optional<Player> profile = playerTracking.find(uuid);
            List<Player> alts = altDetectionService.findAlts(uuid);

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("Lookup: " + target.get().name())
                    .setThumbnail("https://mc-heads.net/avatar/" + uuid);
            profile.ifPresentOrElse(p -> {
                embed.addField("First seen", String.valueOf(p.getFirstSeen()), true);
                embed.addField("Last seen", String.valueOf(p.getLastSeen()), true);
            }, () -> embed.setDescription("No profile on record."));
            embed.addField("Active punishments", String.valueOf(punishmentService.activePunishments(uuid).size()), true);
            embed.addField("Staff notes", String.valueOf(noteService.list(uuid).size()), true);
            embed.addField("Known alts", alts.isEmpty() ? "none" : String.valueOf(alts.size()), true);
            event.getHook().editOriginalEmbeds(embed.build()).queue();
        });
    }

    private void note(SlashCommandInteractionEvent event, String name, String text) {
        UUID staffUuid = linkService.findLinkedUuid(event.getUser().getId()).orElse(null);
        DiscordTargetResolver.resolve(bridge, players, mojangApi, name).thenAccept(target -> {
            if (target.isEmpty()) {
                event.getHook().editOriginal("Could not find a player named `" + name + "`.").queue();
                return;
            }
            noteService.add(target.get().uuid(), staffUuid, "[Discord: " + event.getUser().getAsTag() + "] " + text);
            event.getHook().editOriginal("Note added for **" + target.get().name() + "**.").queue();
            });
    }

    /** §6 - generates a code and tells the member to redeem it with `/link <code>` in-game. */
    private void link(SlashCommandInteractionEvent event) {
        if (linkService.findLinkedUuid(event.getUser().getId()).isPresent()) {
            event.reply("Your Discord account is already linked. Use `/unlink` first to link a different account.")
                    .setEphemeral(true).queue();
            return;
        }
        String code = linkService.generateCode(event.getUser().getId(), event.getUser().getAsTag());
        event.reply("In-game, run `/link " + code + "` within 10 minutes to finish linking your Discord account.")
                .setEphemeral(true).queue();
    }

    private void unlink(SlashCommandInteractionEvent event) {
        boolean removed = linkService.unlinkDiscord(event.getUser().getId());
        event.reply(removed ? "Your Discord account has been unlinked." : "Your Discord account isn't linked to anything.")
                .setEphemeral(true).queue();
    }
}
