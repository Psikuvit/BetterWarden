package me.psikuvit.betterWarden.spigot.command;

import me.psikuvit.betterWarden.core.model.Punishment;
import me.psikuvit.betterWarden.core.model.PunishmentTemplate;
import me.psikuvit.betterWarden.core.model.PunishmentType;
import me.psikuvit.betterWarden.core.repo.PlayerRepository;
import me.psikuvit.betterWarden.core.service.LangService;
import me.psikuvit.betterWarden.core.service.MojangApiService;
import me.psikuvit.betterWarden.core.service.PunishmentService;
import me.psikuvit.betterWarden.core.service.PunishmentTemplateService;
import me.psikuvit.betterWarden.core.util.DurationParser;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * ban/sban/tempban/ipban/kick/skick/mute/smute/tempmute/warn/unban/unmute - same command surface
 * as the Paper path's own PunishmentCommands, ported off Brigadier onto plain CommandExecutor
 * (registered per-name via plugin.yml, no dynamic argument suggestions/types here). Async
 * target resolution and the Async.runOnMain() pattern carry over unchanged - see the Paper path's
 * PunishmentCommands for the full reasoning (Brigadier or not, this plugin still can't block the
 * main thread on a Mojang lookup).
 */
public final class PunishmentCommands {

    private final PunishmentService service;
    private final PunishmentTemplateService templates;
    private final LangService lang;
    private final PlayerRepository players;
    private final MojangApiService mojangApi;
    private final JavaPlugin plugin;
    private final SpigotMsg msg;

    private PunishmentCommands(PunishmentService service, PunishmentTemplateService templates, LangService lang,
                                PlayerRepository players, MojangApiService mojangApi, JavaPlugin plugin, SpigotMsg msg) {
        this.service = service;
        this.templates = templates;
        this.lang = lang;
        this.players = players;
        this.mojangApi = mojangApi;
        this.plugin = plugin;
        this.msg = msg;
    }

    public static void register(JavaPlugin plugin, PunishmentService service, PunishmentTemplateService templates, LangService lang,
                                 PlayerRepository players, MojangApiService mojangApi, SpigotMsg msg) {
        PunishmentCommands commands = new PunishmentCommands(service, templates, lang, players, mojangApi, plugin, msg);
        bind(plugin, "ban", commands.fixed(PunishmentType.BAN, false));
        bind(plugin, "sban", commands.fixed(PunishmentType.BAN, true));
        bind(plugin, "tempban", commands.timed(PunishmentType.TEMPBAN, false));
        bind(plugin, "ipban", commands.wrap(commands::executeIpBan));
        bind(plugin, "kick", commands.fixed(PunishmentType.KICK, false));
        bind(plugin, "skick", commands.fixed(PunishmentType.KICK, true));
        bind(plugin, "mute", commands.fixed(PunishmentType.MUTE, false));
        bind(plugin, "smute", commands.fixed(PunishmentType.MUTE, true));
        bind(plugin, "tempmute", commands.timed(PunishmentType.TEMPMUTE, false));
        bind(plugin, "warn", commands.fixed(PunishmentType.WARN, false));
        bind(plugin, "unban", commands.unpunish(Set.of(PunishmentType.BAN, PunishmentType.TEMPBAN, PunishmentType.IPBAN)));
        bind(plugin, "unmute", commands.unpunish(Set.of(PunishmentType.MUTE, PunishmentType.TEMPMUTE)));
    }

    private static void bind(JavaPlugin plugin, String name, CommandExecutor executor) {
        var command = plugin.getCommand(name);
        if (command != null) {
            command.setExecutor(executor);
        }
    }

    private interface Handler {
        void run(CommandSender sender, String[] args);
    }

    private CommandExecutor fixed(PunishmentType type, boolean silent) {
        return wrap((sender, args) -> {
            if (args.length == 0) {
                msg.send(sender, lang.get("common.player-not-found"));
                return;
            }
            String rawReason = reasonOrDefault(args, 1);
            TargetResolver.resolve(players, mojangApi, args[0]).thenAccept(target ->
                    Async.runOnMain(plugin, () -> issue(sender, target, type, rawReason, null, silent)));
        });
    }

    private CommandExecutor timed(PunishmentType type, boolean silent) {
        return wrap((sender, args) -> {
            if (args.length < 2) {
                msg.send(sender, lang.get("common.player-not-found"));
                return;
            }
            Duration duration;
            try {
                duration = DurationParser.parse(args[1]);
            } catch (IllegalArgumentException e) {
                msg.send(sender, lang.get("punish.invalid-duration", args[1]));
                return;
            }
            String rawReason = reasonOrDefault(args, 2);
            TargetResolver.resolve(players, mojangApi, args[0]).thenAccept(target ->
                    Async.runOnMain(plugin, () -> issue(sender, target, type, rawReason, duration, silent)));
        });
    }

    private void issue(CommandSender sender, Optional<TargetResolver.Target> target, PunishmentType type,
                        String rawReason, Duration duration, boolean silent) {
        if (target.isEmpty()) {
            msg.send(sender, lang.get("common.player-not-found"));
            return;
        }
        if (PunishmentType.MUTE_TYPES.contains(type) && service.activeMute(target.get().uuid()).isPresent()) {
            msg.send(sender, lang.get("punish.already-muted", target.get().name()));
            return;
        }
        UUID staffUuid = sender instanceof Player p ? p.getUniqueId() : null;

        Punishment punishment;
        if (rawReason.startsWith("#")) {
            Optional<PunishmentTemplate> template = templates.find(rawReason.substring(1));
            if (template.isEmpty()) {
                msg.send(sender, lang.get("punish.unknown-template", rawReason));
                return;
            }
            punishment = service.issueFromTemplate(target.get().uuid(), target.get().name(), template.get(), staffUuid, silent, null);
        } else {
            punishment = service.issue(target.get().uuid(), target.get().name(), type, rawReason, staffUuid, duration, silent, null);
        }
        msg.send(sender, lang.get("punish.issued", punishment.getType(), target.get().name(), punishment.getId(), punishment.getReason()));
    }

    private void executeIpBan(CommandSender sender, String[] args) {
        if (args.length == 0) {
            msg.send(sender, lang.get("common.player-not-found"));
            return;
        }
        String reason = reasonOrDefault(args, 1);
        TargetResolver.resolve(players, mojangApi, args[0]).thenAccept(target ->
                Async.runOnMain(plugin, () -> {
                    if (target.isEmpty()) {
                        msg.send(sender, lang.get("common.player-not-found"));
                        return;
                    }
                    UUID staffUuid = sender instanceof Player p ? p.getUniqueId() : null;
                    Punishment punishment = service.issueIpBan(target.get().uuid(), target.get().name(), reason, staffUuid, null, false, null);
                    msg.send(sender, lang.get("punish.ipban-issued", target.get().name(), punishment.getId(), reason));
                }));
    }

    private CommandExecutor unpunish(Set<PunishmentType> types) {
        return wrap((sender, args) -> {
            if (args.length == 0) {
                msg.send(sender, lang.get("common.player-not-found"));
                return;
            }
            String reason = reasonOrDefault(args, 1);
            TargetResolver.resolve(players, mojangApi, args[0]).thenAccept(target ->
                    Async.runOnMain(plugin, () -> {
                        if (target.isEmpty()) {
                            msg.send(sender, lang.get("common.player-not-found"));
                            return;
                        }
                        List<Punishment> active = service.activePunishments(target.get().uuid()).stream()
                                .filter(p -> types.contains(p.getType()))
                                .toList();
                        if (active.isEmpty()) {
                            msg.send(sender, lang.get("punish.no-active-punishment", target.get().name()));
                            return;
                        }
                        UUID staffUuid = sender instanceof Player p ? p.getUniqueId() : null;
                        for (Punishment p : active) {
                            service.revoke(p.getId(), staffUuid, reason);
                        }
                        msg.send(sender, lang.get("punish.revoked", active.size(), target.get().name()));
                    }));
        });
    }

    private String reasonOrDefault(String[] args, int fromIndex) {
        if (args.length <= fromIndex) {
            return lang.get("punish.no-reason");
        }
        return String.join(" ", Arrays.copyOfRange(args, fromIndex, args.length));
    }

    private CommandExecutor wrap(Handler handler) {
        return (sender, command, label, args) -> {
            handler.run(sender, args);
            return true;
        };
    }
}
