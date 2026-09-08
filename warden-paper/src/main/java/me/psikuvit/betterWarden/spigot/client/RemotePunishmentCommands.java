package me.psikuvit.betterWarden.spigot.client;

import me.psikuvit.betterWarden.spigot.command.Async;
import me.psikuvit.betterWarden.spigot.command.SpigotMsg;
import me.psikuvit.betterWarden.spigot.command.TargetResolver;
import me.psikuvit.betterWarden.core.client.RemoteCoreClient;
import me.psikuvit.betterWarden.core.client.RemotePunishmentCache;
import me.psikuvit.betterWarden.core.model.Punishment;
import me.psikuvit.betterWarden.core.model.PunishmentType;
import me.psikuvit.betterWarden.core.network.dto.IssuePunishmentRequest;
import me.psikuvit.betterWarden.core.network.dto.PunishmentDto;
import me.psikuvit.betterWarden.core.network.dto.RevokeRequest;
import me.psikuvit.betterWarden.core.service.LangService;
import me.psikuvit.betterWarden.core.service.MojangApiService;
import me.psikuvit.betterWarden.core.util.DurationParser;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * CLIENT-mode punishment commands - a scoped-down version of PunishmentCommands, same as
 * warden-paper's own: no silent variants, no #template shorthand, no /ipban (those all need
 * core-side services CLIENT mode doesn't have a local copy of).
 */
public final class RemotePunishmentCommands {

    private final RemoteCoreClient client;
    private final RemotePunishmentCache cache;
    private final LangService lang;
    private final MojangApiService mojangApi;
    private final JavaPlugin plugin;
    private final SpigotMsg msg;

    private RemotePunishmentCommands(RemoteCoreClient client, RemotePunishmentCache cache, LangService lang,
                                      MojangApiService mojangApi, JavaPlugin plugin, SpigotMsg msg) {
        this.client = client;
        this.cache = cache;
        this.lang = lang;
        this.mojangApi = mojangApi;
        this.plugin = plugin;
        this.msg = msg;
    }

    public static void register(JavaPlugin plugin, RemoteCoreClient client, RemotePunishmentCache cache,
                                 LangService lang, MojangApiService mojangApi, SpigotMsg msg) {
        RemotePunishmentCommands commands = new RemotePunishmentCommands(client, cache, lang, mojangApi, plugin, msg);
        bind(plugin, "ban", commands.fixed(PunishmentType.BAN));
        bind(plugin, "tempban", commands.timed(PunishmentType.TEMPBAN));
        bind(plugin, "kick", commands.fixed(PunishmentType.KICK));
        bind(plugin, "mute", commands.fixed(PunishmentType.MUTE));
        bind(plugin, "tempmute", commands.timed(PunishmentType.TEMPMUTE));
        bind(plugin, "warn", commands.fixed(PunishmentType.WARN));
        bind(plugin, "unban", commands.unpunish(Set.of(PunishmentType.BAN, PunishmentType.TEMPBAN)));
        bind(plugin, "unmute", commands.unpunish(Set.of(PunishmentType.MUTE, PunishmentType.TEMPMUTE)));
    }

    private static void bind(JavaPlugin plugin, String name, CommandExecutor executor) {
        var command = plugin.getCommand(name);
        if (command != null) {
            command.setExecutor(executor);
        }
    }

    private CommandExecutor fixed(PunishmentType type) {
        return (sender, cmd, label, args) -> {
            issue(sender, args, type, null);
            return true;
        };
    }

    private CommandExecutor timed(PunishmentType type) {
        return (sender, cmd, label, args) -> {
            if (args.length < 2) {
                msg.send(sender, lang.get("common.player-not-found"));
                return true;
            }
            Duration duration;
            try {
                duration = DurationParser.parse(args[1]);
            } catch (IllegalArgumentException e) {
                msg.send(sender, lang.get("punish.invalid-duration", args[1]));
                return true;
            }
            issue(sender, args, type, duration);
            return true;
        };
    }

    private void issue(CommandSender sender, String[] args, PunishmentType type, Duration duration) {
        if (args.length == 0) {
            msg.send(sender, lang.get("common.player-not-found"));
            return;
        }
        UUID staffUuid = sender instanceof Player p ? p.getUniqueId() : null;
        String reason = reasonOrDefault(args, duration == null ? 1 : 2);
        TargetResolver.resolve(mojangApi, args[0]).thenAccept(target ->
                Async.runOnMain(plugin, () -> {
                    if (target.isEmpty()) {
                        msg.send(sender, lang.get("common.player-not-found"));
                        return;
                    }
                    if (PunishmentType.MUTE_TYPES.contains(type) && cache.activeMute(target.get().uuid()).isPresent()) {
                        msg.send(sender, lang.get("punish.already-muted", target.get().name()));
                        return;
                    }
                    IssuePunishmentRequest req = new IssuePunishmentRequest(target.get().uuid().toString(), target.get().name(),
                            type, reason, staffUuid == null ? null : staffUuid.toString(),
                            duration == null ? null : duration.toSeconds(), false);
                    Optional<PunishmentDto> result = client.issue(req);
                    if (result.isPresent()) {
                        PunishmentDto dto = result.get();
                        msg.send(sender, lang.get("punish.issued", dto.type(), target.get().name(), dto.id(), dto.reason()));
                    } else {
                        msg.send(sender, lang.get("punish.queued", type, target.get().name()));
                    }
                }));
    }

    private CommandExecutor unpunish(Set<PunishmentType> types) {
        return (sender, cmd, label, args) -> {
            if (args.length == 0) {
                msg.send(sender, lang.get("common.player-not-found"));
                return true;
            }
            UUID staffUuid = sender instanceof Player p ? p.getUniqueId() : null;
            String reason = reasonOrDefault(args, 1);
            TargetResolver.resolve(mojangApi, args[0]).thenAccept(target ->
                    Async.runOnMain(plugin, () -> {
                        if (target.isEmpty()) {
                            msg.send(sender, lang.get("common.player-not-found"));
                            return;
                        }
                        Optional<Punishment> active = types.contains(PunishmentType.MUTE)
                                ? cache.activeMute(target.get().uuid())
                                : cache.activeBan(target.get().uuid());
                        if (active.isEmpty() || !types.contains(active.get().getType())) {
                            msg.send(sender, lang.get("punish.no-active-punishment", target.get().name()));
                            return;
                        }
                        boolean ok = client.revoke(active.get().getId(), new RevokeRequest(staffUuid == null ? null : staffUuid.toString(), reason));
                        msg.send(sender, lang.get(ok ? "punish.revoked" : "punish.revoke-queued", 1, target.get().name()));
                    }));
            return true;
        };
    }

    private String reasonOrDefault(String[] args, int fromIndex) {
        if (args.length <= fromIndex) {
            return lang.get("punish.no-reason");
        }
        return String.join(" ", Arrays.copyOfRange(args, fromIndex, args.length));
    }
}
