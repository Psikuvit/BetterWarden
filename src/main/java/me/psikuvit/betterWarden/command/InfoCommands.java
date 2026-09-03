package me.psikuvit.betterWarden.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.psikuvit.betterWarden.core.model.Punishment;
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

    private InfoCommands(PunishmentService punishmentService, StaffNoteService noteService) {
        this.punishmentService = punishmentService;
        this.noteService = noteService;
    }

    public static void register(JavaPlugin plugin, PunishmentService punishmentService, StaffNoteService noteService) {
        InfoCommands commands = new InfoCommands(punishmentService, noteService);
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands registrar = event.registrar();
            registrar.register(commands.history().build(), "View a player's punishment history");
            registrar.register(commands.note().build(), "Add a staff note to a player");
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

    private int executeHistory(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Optional<TargetResolver.Target> target = TargetResolver.resolve(StringArgumentType.getString(ctx, "player"));
        if (target.isEmpty()) {
            sender.sendMessage("Player not found.");
            return 0;
        }
        List<Punishment> history = punishmentService.history(target.get().uuid(), 10);
        if (history.isEmpty()) {
            sender.sendMessage(target.get().name() + " has no punishment history.");
            return Command.SINGLE_SUCCESS;
        }
        sender.sendMessage("Punishment history for " + target.get().name() + ":");
        for (Punishment p : history) {
            String status = p.isActive() ? "ACTIVE" : "expired/revoked";
            sender.sendMessage("#" + p.getId() + " " + p.getType() + " [" + status + "] " + p.getReason());
        }
        return Command.SINGLE_SUCCESS;
    }

    private int executeNote(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Optional<TargetResolver.Target> target = TargetResolver.resolve(StringArgumentType.getString(ctx, "player"));
        if (target.isEmpty()) {
            sender.sendMessage("Player not found.");
            return 0;
        }
        String text = StringArgumentType.getString(ctx, "text");
        UUID staffUuid = sender instanceof Player p ? p.getUniqueId() : null;
        noteService.add(target.get().uuid(), staffUuid, text);
        sender.sendMessage("Note added to " + target.get().name() + ".");
        return Command.SINGLE_SUCCESS;
    }
}
