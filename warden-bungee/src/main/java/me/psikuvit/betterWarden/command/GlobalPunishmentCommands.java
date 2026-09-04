package me.psikuvit.betterWarden.command;

import me.psikuvit.betterWarden.core.model.Punishment;
import me.psikuvit.betterWarden.core.model.PunishmentType;
import me.psikuvit.betterWarden.core.repo.PlayerRepository;
import me.psikuvit.betterWarden.core.service.LangService;
import me.psikuvit.betterWarden.core.service.PunishmentService;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Plugin;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

/**
 * Network-wide punishments issued from the proxy - same as warden-velocity's own
 * GlobalPunishmentCommands, ported to BungeeCord's plain Command class (no Brigadier here,
 * so no suggestions/argument types - just hand-parsed args, same as any legacy Bukkit command).
 */
public final class GlobalPunishmentCommands {

    private final ProxyServer server;
    private final PlayerRepository players;
    private final PunishmentService punishmentService;
    private final LangService lang;
    private final BungeeMsg msg;

    private GlobalPunishmentCommands(ProxyServer server, PlayerRepository players,
                                      PunishmentService punishmentService, LangService lang, BungeeMsg msg) {
        this.server = server;
        this.players = players;
        this.punishmentService = punishmentService;
        this.lang = lang;
        this.msg = msg;
    }

    public static void register(Plugin plugin, ProxyServer server, PlayerRepository players,
                                 PunishmentService punishmentService, LangService lang, BungeeMsg msg) {
        GlobalPunishmentCommands commands = new GlobalPunishmentCommands(server, players, punishmentService, lang, msg);
        server.getPluginManager().registerCommand(plugin, commands.punish("gban", "warden.ban", PunishmentType.BAN));
        server.getPluginManager().registerCommand(plugin, commands.punish("gmute", "warden.mute", PunishmentType.MUTE));
        server.getPluginManager().registerCommand(plugin, commands.punish("gkick", "warden.kick", PunishmentType.KICK));
    }

    private Command punish(String name, String permission, PunishmentType type) {
        return new Command(name, permission) {
            @Override
            public void execute(CommandSender sender, String[] args) {
                if (args.length == 0) {
                    msg.send(sender, lang.get("common.player-not-found"));
                    return;
                }
                Optional<ProxyTargetResolver.Target> target = ProxyTargetResolver.resolve(server, players, args[0]);
                if (target.isEmpty()) {
                    msg.send(sender, lang.get("common.player-not-found"));
                    return;
                }
                if (PunishmentType.MUTE_TYPES.contains(type) && punishmentService.activeMute(target.get().uuid()).isPresent()) {
                    msg.send(sender, lang.get("punish.already-muted", target.get().name()));
                    return;
                }
                String reason = args.length > 1
                        ? String.join(" ", Arrays.copyOfRange(args, 1, args.length))
                        : lang.get("punish.no-reason");
                UUID staffUuid = sender instanceof ProxiedPlayer p ? p.getUniqueId() : null;

                Punishment punishment = punishmentService.issue(target.get().uuid(), target.get().name(), type, reason,
                        staffUuid, null, false, null);
                msg.send(sender, lang.get("punish.issued", punishment.getType(), target.get().name(),
                        punishment.getId(), punishment.getReason()));
            }
        };
    }
}
