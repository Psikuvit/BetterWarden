package me.psikuvit.betterWarden.command;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import me.psikuvit.betterWarden.core.model.Punishment;
import me.psikuvit.betterWarden.core.model.PunishmentType;
import me.psikuvit.betterWarden.core.repo.PlayerRepository;
import me.psikuvit.betterWarden.core.service.LangService;
import me.psikuvit.betterWarden.core.service.PunishmentService;

import java.util.Optional;
import java.util.UUID;

/**
 * Network-wide punishments issued from the proxy: since VelocityBridge kicks/messages the
 * player directly through the proxy's own connection, these apply regardless of which backend
 * the target is on - no per-server relay needed.
 */
public final class GlobalPunishmentCommands {

    private final ProxyServer server;
    private final PlayerRepository players;
    private final PunishmentService punishmentService;
    private final LangService lang;

    private GlobalPunishmentCommands(ProxyServer server, PlayerRepository players,
                                      PunishmentService punishmentService, LangService lang) {
        this.server = server;
        this.players = players;
        this.punishmentService = punishmentService;
        this.lang = lang;
    }

    public static void register(ProxyServer server, PlayerRepository players,
                                 PunishmentService punishmentService, LangService lang) {
        GlobalPunishmentCommands commands = new GlobalPunishmentCommands(server, players, punishmentService, lang);
        CommandManager manager = server.getCommandManager();
        manager.register(manager.metaBuilder("gban").plugin(commands).build(),
                commands.punish(PunishmentType.BAN, "warden.ban"));
        manager.register(manager.metaBuilder("gmute").plugin(commands).build(),
                commands.punish(PunishmentType.MUTE, "warden.mute"));
        manager.register(manager.metaBuilder("gkick").plugin(commands).build(),
                commands.punish(PunishmentType.KICK, "warden.kick"));
    }

    private SimpleCommand punish(PunishmentType type, String permission) {
        return new SimpleCommand() {
            @Override
            public boolean hasPermission(Invocation invocation) {
                return invocation.source().hasPermission(permission);
            }

            @Override
            public void execute(Invocation invocation) {
                CommandSource source = invocation.source();
                String[] args = invocation.arguments();
                if (args.length == 0) {
                    source.sendRichMessage(lang.get("common.player-not-found"));
                    return;
                }

                Optional<ProxyTargetResolver.Target> target = ProxyTargetResolver.resolve(server, players, args[0]);
                if (target.isEmpty()) {
                    source.sendRichMessage(lang.get("common.player-not-found"));
                    return;
                }
                String reason = args.length > 1
                        ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length))
                        : lang.get("punish.no-reason");
                UUID staffUuid = source instanceof Player p ? p.getUniqueId() : null;

                Punishment punishment = punishmentService.issue(target.get().uuid(), target.get().name(), type, reason,
                        staffUuid, null, false, null);
                source.sendRichMessage(lang.get("punish.issued", punishment.getType(), target.get().name(),
                        punishment.getId(), punishment.getReason()));
            }
        };
    }
}
