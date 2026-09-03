package me.psikuvit.betterWarden.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.psikuvit.betterWarden.core.model.Punishment;
import me.psikuvit.betterWarden.core.model.PunishmentType;
import me.psikuvit.betterWarden.core.service.PunishmentService;
import me.psikuvit.betterWarden.core.util.DurationParser;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;

public final class PunishmentCommands {

    private static final List<String> DURATION_PRESETS = List.of("30m", "1h", "6h", "1d", "3d", "7d", "30d", "perm");
    private static final SuggestionProvider<CommandSourceStack> PLAYER_SUGGESTIONS = PlayerSuggestions.ONLINE_PLAYERS;

    private static final SuggestionProvider<CommandSourceStack> DURATION_SUGGESTIONS = (ctx, builder) -> {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (String d : DURATION_PRESETS) {
            if (d.startsWith(remaining)) {
                builder.suggest(d);
            }
        }
        return builder.buildFuture();
    };

    private final PunishmentService service;

    private PunishmentCommands(PunishmentService service) {
        this.service = service;
    }

    public static void register(JavaPlugin plugin, PunishmentService service) {
        PunishmentCommands commands = new PunishmentCommands(service);
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands registrar = event.registrar();
            registrar.register(commands.punishFixed("ban", "warden.ban", PunishmentType.BAN, false).build(), "Ban a player");
            registrar.register(commands.punishFixed("sban", "warden.ban", PunishmentType.BAN, true).build(), "Silently ban a player");
            registrar.register(commands.punishTimed("tempban", "warden.ban", PunishmentType.TEMPBAN, false).build(), "Temporarily ban a player");
            registrar.register(commands.ipban().build(), "IP-ban a player");
            registrar.register(commands.punishFixed("kick", "warden.kick", PunishmentType.KICK, false).build(), "Kick a player");
            registrar.register(commands.punishFixed("skick", "warden.kick", PunishmentType.KICK, true).build(), "Silently kick a player");
            registrar.register(commands.punishFixed("mute", "warden.mute", PunishmentType.MUTE, false).build(), "Mute a player");
            registrar.register(commands.punishFixed("smute", "warden.mute", PunishmentType.MUTE, true).build(), "Silently mute a player");
            registrar.register(commands.punishTimed("tempmute", "warden.mute", PunishmentType.TEMPMUTE, false).build(), "Temporarily mute a player");
            registrar.register(commands.punishFixed("warn", "warden.warn", PunishmentType.WARN, false).build(), "Warn a player");
            registrar.register(commands.unpunish("unban", "warden.ban", Set.of(PunishmentType.BAN, PunishmentType.TEMPBAN, PunishmentType.IPBAN)).build(), "Unban a player");
            registrar.register(commands.unpunish("unmute", "warden.mute", Set.of(PunishmentType.MUTE, PunishmentType.TEMPMUTE)).build(), "Unmute a player");
        });
    }

    private LiteralArgumentBuilder<CommandSourceStack> punishFixed(String name, String permission, PunishmentType type, boolean silent) {
        return literal(name)
                .requires(src -> src.getSender().hasPermission(permission))
                .then(argument("player", StringArgumentType.word())
                        .suggests(PLAYER_SUGGESTIONS)
                        .executes(ctx -> executeFixed(ctx, type, silent))
                        .then(argument("reason", StringArgumentType.greedyString())
                                .executes(ctx -> executeFixed(ctx, type, silent))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> punishTimed(String name, String permission, PunishmentType type, boolean silent) {
        return literal(name)
                .requires(src -> src.getSender().hasPermission(permission))
                .then(argument("player", StringArgumentType.word())
                        .suggests(PLAYER_SUGGESTIONS)
                        .then(argument("duration", StringArgumentType.word())
                                .suggests(DURATION_SUGGESTIONS)
                                .executes(ctx -> executeTimed(ctx, type, silent))
                                .then(argument("reason", StringArgumentType.greedyString())
                                        .executes(ctx -> executeTimed(ctx, type, silent)))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> ipban() {
        return literal("ipban")
                .requires(src -> src.getSender().hasPermission("warden.ban"))
                .then(argument("player", StringArgumentType.word())
                        .suggests(PLAYER_SUGGESTIONS)
                        .executes(this::executeIpBan)
                        .then(argument("reason", StringArgumentType.greedyString())
                                .executes(this::executeIpBan)));
    }

    private LiteralArgumentBuilder<CommandSourceStack> unpunish(String name, String permission, Set<PunishmentType> types) {
        return literal(name)
                .requires(src -> src.getSender().hasPermission(permission))
                .then(argument("player", StringArgumentType.word())
                        .suggests(PLAYER_SUGGESTIONS)
                        .executes(ctx -> executeUnpunish(ctx, types))
                        .then(argument("reason", StringArgumentType.greedyString())
                                .executes(ctx -> executeUnpunish(ctx, types))));
    }

    private int executeFixed(CommandContext<CommandSourceStack> ctx, PunishmentType type, boolean silent) {
        CommandSender sender = ctx.getSource().getSender();
        Optional<TargetResolver.Target> target = TargetResolver.resolve(StringArgumentType.getString(ctx, "player"));
        if (target.isEmpty()) {
            sender.sendMessage("Player not found.");
            return 0;
        }
        String reason = reasonOrDefault(ctx);
        UUID staffUuid = sender instanceof Player p ? p.getUniqueId() : null;
        Punishment punishment = service.issue(target.get().uuid(), target.get().name(), type, reason, staffUuid, null, silent, null);
        sender.sendMessage(type.name() + " issued to " + target.get().name() + " (#" + punishment.getId() + "): " + reason);
        return Command.SINGLE_SUCCESS;
    }

    private int executeTimed(CommandContext<CommandSourceStack> ctx, PunishmentType type, boolean silent) {
        CommandSender sender = ctx.getSource().getSender();
        String durationStr = StringArgumentType.getString(ctx, "duration");
        Duration duration;
        try {
            duration = DurationParser.parse(durationStr);
        } catch (IllegalArgumentException e) {
            sender.sendMessage("Invalid duration: " + durationStr);
            return 0;
        }
        Optional<TargetResolver.Target> target = TargetResolver.resolve(StringArgumentType.getString(ctx, "player"));
        if (target.isEmpty()) {
            sender.sendMessage("Player not found.");
            return 0;
        }
        String reason = reasonOrDefault(ctx);
        UUID staffUuid = sender instanceof Player p ? p.getUniqueId() : null;
        Punishment punishment = service.issue(target.get().uuid(), target.get().name(), type, reason, staffUuid, duration, silent, null);
        sender.sendMessage(type.name() + " issued to " + target.get().name() + " (#" + punishment.getId() + "): " + reason);
        return Command.SINGLE_SUCCESS;
    }

    private int executeIpBan(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Optional<TargetResolver.Target> target = TargetResolver.resolve(StringArgumentType.getString(ctx, "player"));
        if (target.isEmpty()) {
            sender.sendMessage("Player not found.");
            return 0;
        }
        String reason = reasonOrDefault(ctx);
        UUID staffUuid = sender instanceof Player p ? p.getUniqueId() : null;
        Punishment punishment = service.issueIpBan(target.get().uuid(), target.get().name(), reason, staffUuid, null, false, null);
        sender.sendMessage("IPBAN issued to " + target.get().name() + " (#" + punishment.getId() + "): " + reason);
        return Command.SINGLE_SUCCESS;
    }

    private int executeUnpunish(CommandContext<CommandSourceStack> ctx, Set<PunishmentType> types) {
        CommandSender sender = ctx.getSource().getSender();
        Optional<TargetResolver.Target> target = TargetResolver.resolve(StringArgumentType.getString(ctx, "player"));
        if (target.isEmpty()) {
            sender.sendMessage("Player not found.");
            return 0;
        }
        List<Punishment> active = service.activePunishments(target.get().uuid()).stream()
                .filter(p -> types.contains(p.getType()))
                .toList();
        if (active.isEmpty()) {
            sender.sendMessage(target.get().name() + " has no matching active punishment.");
            return 0;
        }
        String reason = reasonOrDefault(ctx);
        UUID staffUuid = sender instanceof Player p ? p.getUniqueId() : null;
        for (Punishment p : active) {
            service.revoke(p.getId(), staffUuid, reason);
        }
        sender.sendMessage("Revoked " + active.size() + " punishment(s) for " + target.get().name());
        return Command.SINGLE_SUCCESS;
    }

    private static String reasonOrDefault(CommandContext<CommandSourceStack> ctx) {
        try {
            return StringArgumentType.getString(ctx, "reason");
        } catch (IllegalArgumentException e) {
            return "No reason specified";
        }
    }
}
