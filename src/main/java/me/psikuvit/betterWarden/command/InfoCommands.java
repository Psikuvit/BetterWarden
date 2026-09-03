package me.psikuvit.betterWarden.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.psikuvit.betterWarden.core.model.Punishment;
import me.psikuvit.betterWarden.core.service.AltDetectionService;
import me.psikuvit.betterWarden.core.service.LangService;
import me.psikuvit.betterWarden.core.service.PlayerTrackingService;
import me.psikuvit.betterWarden.core.service.PunishmentService;
import me.psikuvit.betterWarden.core.service.StaffNoteService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;

public final class InfoCommands {

    private final PunishmentService punishmentService;
    private final StaffNoteService noteService;
    private final AltDetectionService altDetectionService;
    private final PlayerTrackingService playerTracking;
    private final LangService lang;

    private InfoCommands(PunishmentService punishmentService, StaffNoteService noteService,
                          AltDetectionService altDetectionService, PlayerTrackingService playerTracking, LangService lang) {
        this.punishmentService = punishmentService;
        this.noteService = noteService;
        this.altDetectionService = altDetectionService;
        this.playerTracking = playerTracking;
        this.lang = lang;
    }

    public static void register(JavaPlugin plugin, PunishmentService punishmentService, StaffNoteService noteService,
                                 AltDetectionService altDetectionService, PlayerTrackingService playerTracking, LangService lang) {
        InfoCommands commands = new InfoCommands(punishmentService, noteService, altDetectionService, playerTracking, lang);
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands registrar = event.registrar();
            registrar.register(commands.history().build(), "View a player's punishment history");
            registrar.register(commands.note().build(), "Add a staff note to a player");
            registrar.register(commands.alts().build(), "List a player's known alt accounts");
            registrar.register(commands.lookup().build(), "View a player's profile summary");
        });
    }

    private LiteralArgumentBuilder<CommandSourceStack> history() {
        return literal("history")
                .requires(src -> src.getSender().hasPermission("warden.history"))
                .then(argument("player", StringArgumentType.word())
                        .suggests(PlayerSuggestions.ONLINE_PLAYERS)
                        .executes(this::executeHistory));
    }

    private LiteralArgumentBuilder<CommandSourceStack> note() {
        return literal("note")
                .requires(src -> src.getSender().hasPermission("warden.note"))
                .then(argument("player", StringArgumentType.word())
                        .suggests(PlayerSuggestions.ONLINE_PLAYERS)
                        .then(argument("text", StringArgumentType.greedyString())
                                .executes(this::executeNote)));
    }

    private LiteralArgumentBuilder<CommandSourceStack> alts() {
        return literal("alts")
                .requires(src -> src.getSender().hasPermission("warden.alts"))
                .then(argument("player", StringArgumentType.word())
                        .suggests(PlayerSuggestions.ONLINE_PLAYERS)
                        .executes(this::executeAlts));
    }

    private int executeAlts(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Optional<TargetResolver.Target> target = TargetResolver.resolve(StringArgumentType.getString(ctx, "player"));
        if (target.isEmpty()) {
            Msg.send(sender, lang.get("common.player-not-found"));
            return 0;
        }
        List<me.psikuvit.betterWarden.core.model.Player> alts = altDetectionService.findAlts(target.get().uuid());
        if (alts.isEmpty()) {
            Msg.send(sender, lang.get("alts.none", target.get().name()));
            return Command.SINGLE_SUCCESS;
        }
        Msg.send(sender, lang.get("alts.header", target.get().name()));
        for (me.psikuvit.betterWarden.core.model.Player alt : alts) {
            Msg.send(sender, lang.get("alts.line", alt.getLastName()));
        }
        return Command.SINGLE_SUCCESS;
    }

    private LiteralArgumentBuilder<CommandSourceStack> lookup() {
        return literal("lookup")
                .requires(src -> src.getSender().hasPermission("warden.lookup"))
                .then(argument("player", StringArgumentType.word())
                        .suggests(PlayerSuggestions.ONLINE_PLAYERS)
                        .executes(this::executeLookup));
    }

    private int executeLookup(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Optional<TargetResolver.Target> target = TargetResolver.resolve(StringArgumentType.getString(ctx, "player"));
        if (target.isEmpty()) {
            Msg.send(sender, lang.get("common.player-not-found"));
            return 0;
        }
        UUID uuid = target.get().uuid();
        Optional<me.psikuvit.betterWarden.core.model.Player> player = playerTracking.find(uuid);

        Msg.send(sender, lang.get("lookup.header", target.get().name()));
        player.ifPresentOrElse(p -> {
            Msg.send(sender, lang.get("lookup.first-seen", p.getFirstSeen()));
            Msg.send(sender, lang.get("lookup.last-seen", p.getLastSeen()));
        }, () -> Msg.send(sender, lang.get("lookup.no-profile")));
        Msg.send(sender, lang.get("lookup.active-punishments", punishmentService.activePunishments(uuid).size()));
        Msg.send(sender, lang.get("lookup.staff-notes", noteService.list(uuid).size()));
        Msg.send(sender, lang.get("lookup.known-alts", altDetectionService.findAlts(uuid).size()));
        return Command.SINGLE_SUCCESS;
    }

    private int executeHistory(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Optional<TargetResolver.Target> target = TargetResolver.resolve(StringArgumentType.getString(ctx, "player"));
        if (target.isEmpty()) {
            Msg.send(sender, lang.get("common.player-not-found"));
            return 0;
        }
        List<Punishment> history = punishmentService.history(target.get().uuid(), 10);
        if (history.isEmpty()) {
            Msg.send(sender, lang.get("history.empty", target.get().name()));
            return Command.SINGLE_SUCCESS;
        }
        Msg.send(sender, lang.get("history.header", target.get().name()));
        for (Punishment p : history) {
            String status = p.isActive() ? lang.get("history.status-active") : lang.get("history.status-inactive");
            Msg.send(sender, lang.get("history.line", p.getId(), p.getType(), status, p.getReason()));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int executeNote(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Optional<TargetResolver.Target> target = TargetResolver.resolve(StringArgumentType.getString(ctx, "player"));
        if (target.isEmpty()) {
            Msg.send(sender, lang.get("common.player-not-found"));
            return 0;
        }
        String text = StringArgumentType.getString(ctx, "text");
        UUID staffUuid = sender instanceof Player p ? p.getUniqueId() : null;
        noteService.add(target.get().uuid(), staffUuid, text);
        Msg.send(sender, lang.get("note.added", target.get().name()));
        return Command.SINGLE_SUCCESS;
    }
}
