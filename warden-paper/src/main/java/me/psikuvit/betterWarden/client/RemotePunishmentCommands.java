package me.psikuvit.betterWarden.client;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.psikuvit.betterWarden.command.Msg;
import me.psikuvit.betterWarden.command.TargetResolver;
import me.psikuvit.betterWarden.core.model.Punishment;
import me.psikuvit.betterWarden.core.model.PunishmentType;
import me.psikuvit.betterWarden.core.network.dto.IssuePunishmentRequest;
import me.psikuvit.betterWarden.core.network.dto.PunishmentDto;
import me.psikuvit.betterWarden.core.network.dto.RevokeRequest;
import me.psikuvit.betterWarden.core.service.LangService;
import me.psikuvit.betterWarden.core.util.DurationParser;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;

/**
 * CLIENT-mode punishment commands - a scoped-down version of PunishmentCommands: no silent
 * variants, no #template shorthand, no /ipban (those all need core-side services CLIENT mode
 * doesn't have a local copy of). Covers /ban /tempban /mute /tempmute /kick /warn /unban /unmute,
 * issued via RemoteCoreClient instead of a local PunishmentService.
 */
public final class RemotePunishmentCommands {

    private final RemoteCoreClient client;
    private final RemotePunishmentCache cache;
    private final LangService lang;

    private RemotePunishmentCommands(RemoteCoreClient client, RemotePunishmentCache cache, LangService lang) {
        this.client = client;
        this.cache = cache;
        this.lang = lang;
    }

    public static void register(JavaPlugin plugin, RemoteCoreClient client, RemotePunishmentCache cache, LangService lang) {
        RemotePunishmentCommands commands = new RemotePunishmentCommands(client, cache, lang);
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands registrar = event.registrar();
            registrar.register(commands.fixed("ban", "warden.ban", PunishmentType.BAN).build(), "Ban a player");
            registrar.register(commands.timed("tempban", "warden.ban", PunishmentType.TEMPBAN).build(), "Temporarily ban a player");
            registrar.register(commands.fixed("kick", "warden.kick", PunishmentType.KICK).build(), "Kick a player");
            registrar.register(commands.fixed("mute", "warden.mute", PunishmentType.MUTE).build(), "Mute a player");
            registrar.register(commands.timed("tempmute", "warden.mute", PunishmentType.TEMPMUTE).build(), "Temporarily mute a player");
            registrar.register(commands.fixed("warn", "warden.warn", PunishmentType.WARN).build(), "Warn a player");
            registrar.register(commands.unpunish("unban", "warden.ban", Set.of(PunishmentType.BAN, PunishmentType.TEMPBAN)).build(), "Unban a player");
            registrar.register(commands.unpunish("unmute", "warden.mute", Set.of(PunishmentType.MUTE, PunishmentType.TEMPMUTE)).build(), "Unmute a player");
        });
    }

    private LiteralArgumentBuilder<CommandSourceStack> fixed(String name, String permission, PunishmentType type) {
        return literal(name)
                .requires(src -> src.getSender().hasPermission(permission))
                .then(argument("player", StringArgumentType.word())
                        .executes(ctx -> executeFixed(ctx, type))
                        .then(argument("reason", StringArgumentType.greedyString())
                                .executes(ctx -> executeFixed(ctx, type))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> timed(String name, String permission, PunishmentType type) {
        return literal(name)
                .requires(src -> src.getSender().hasPermission(permission))
                .then(argument("player", StringArgumentType.word())
                        .then(argument("duration", StringArgumentType.word())
                                .executes(ctx -> executeTimed(ctx, type))
                                .then(argument("reason", StringArgumentType.greedyString())
                                        .executes(ctx -> executeTimed(ctx, type)))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> unpunish(String name, String permission, Set<PunishmentType> types) {
        return literal(name)
                .requires(src -> src.getSender().hasPermission(permission))
                .then(argument("player", StringArgumentType.word())
                        .executes(ctx -> executeUnpunish(ctx, types))
                        .then(argument("reason", StringArgumentType.greedyString())
                                .executes(ctx -> executeUnpunish(ctx, types))));
    }

    private int executeFixed(CommandContext<CommandSourceStack> ctx, PunishmentType type) {
        return issue(ctx, type, null);
    }

    private int executeTimed(CommandContext<CommandSourceStack> ctx, PunishmentType type) {
        CommandSender sender = ctx.getSource().getSender();
        String durationStr = StringArgumentType.getString(ctx, "duration");
        Duration duration;
        try {
            duration = DurationParser.parse(durationStr);
        } catch (IllegalArgumentException e) {
            Msg.send(sender, lang.get("punish.invalid-duration", durationStr));
            return 0;
        }
        return issue(ctx, type, duration);
    }

    private int issue(CommandContext<CommandSourceStack> ctx, PunishmentType type, Duration duration) {
        CommandSender sender = ctx.getSource().getSender();
        Optional<TargetResolver.Target> target = TargetResolver.resolve(StringArgumentType.getString(ctx, "player"));
        if (target.isEmpty()) {
            Msg.send(sender, lang.get("common.player-not-found"));
            return 0;
        }
        if (PunishmentType.MUTE_TYPES.contains(type) && cache.activeMute(target.get().uuid()).isPresent()) {
            Msg.send(sender, lang.get("punish.already-muted", target.get().name()));
            return 0;
        }
        UUID staffUuid = sender instanceof Player p ? p.getUniqueId() : null;
        String reason = reasonOrDefault(ctx);

        IssuePunishmentRequest req = new IssuePunishmentRequest(target.get().uuid().toString(), target.get().name(),
                type, reason, staffUuid == null ? null : staffUuid.toString(),
                duration == null ? null : duration.toSeconds(), false);
        Optional<PunishmentDto> result = client.issue(req);
        if (result.isPresent()) {
            PunishmentDto dto = result.get();
            Msg.send(sender, lang.get("punish.issued", dto.type(), target.get().name(), dto.id(), dto.reason()));
        } else {
            Msg.send(sender, lang.get("punish.queued", type, target.get().name()));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int executeUnpunish(CommandContext<CommandSourceStack> ctx, Set<PunishmentType> types) {
        CommandSender sender = ctx.getSource().getSender();
        Optional<TargetResolver.Target> target = TargetResolver.resolve(StringArgumentType.getString(ctx, "player"));
        if (target.isEmpty()) {
            Msg.send(sender, lang.get("common.player-not-found"));
            return 0;
        }
        Optional<Punishment> active = types.contains(PunishmentType.MUTE)
                ? cache.activeMute(target.get().uuid())
                : cache.activeBan(target.get().uuid());
        if (active.isEmpty() || !types.contains(active.get().getType())) {
            Msg.send(sender, lang.get("punish.no-active-punishment", target.get().name()));
            return 0;
        }
        UUID staffUuid = sender instanceof Player p ? p.getUniqueId() : null;
        String reason = reasonOrDefault(ctx);
        boolean ok = client.revoke(active.get().getId(), new RevokeRequest(staffUuid == null ? null : staffUuid.toString(), reason));
        Msg.send(sender, lang.get(ok ? "punish.revoked" : "punish.revoke-queued", 1, target.get().name()));
        return Command.SINGLE_SUCCESS;
    }

    private String reasonOrDefault(CommandContext<CommandSourceStack> ctx) {
        try {
            return StringArgumentType.getString(ctx, "reason");
        } catch (IllegalArgumentException e) {
            return lang.get("punish.no-reason");
        }
    }
}
