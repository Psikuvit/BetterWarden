package me.psikuvit.betterWarden.command;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import me.psikuvit.betterWarden.core.client.RemoteCoreClient;
import me.psikuvit.betterWarden.core.client.RemotePunishmentCache;
import me.psikuvit.betterWarden.core.model.PunishmentType;
import me.psikuvit.betterWarden.core.network.dto.IssuePunishmentRequest;
import me.psikuvit.betterWarden.core.network.dto.PunishmentDto;
import me.psikuvit.betterWarden.core.service.LangService;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

/**
 * CLIENT-mode /gban /gmute /gkick - issues over RemoteCoreClient instead of a local
 * PunishmentService. Scoped down from GlobalPunishmentCommands: online players only, no offline
 * fallback - that needs its own player-lookup REST endpoint, which doesn't exist yet (PLAN.md).
 */
public final class RemoteGlobalPunishmentCommands {

    private final ProxyServer server;
    private final RemoteCoreClient client;
    private final RemotePunishmentCache cache;
    private final LangService lang;

    private RemoteGlobalPunishmentCommands(ProxyServer server, RemoteCoreClient client,
                                            RemotePunishmentCache cache, LangService lang) {
        this.server = server;
        this.client = client;
        this.cache = cache;
        this.lang = lang;
    }

    public static void register(ProxyServer server, RemoteCoreClient client, RemotePunishmentCache cache, LangService lang) {
        RemoteGlobalPunishmentCommands commands = new RemoteGlobalPunishmentCommands(server, client, cache, lang);
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
                Optional<Player> target = server.getPlayer(args[0]);
                if (target.isEmpty()) {
                    source.sendRichMessage(lang.get("common.player-not-found"));
                    return;
                }
                Player targetPlayer = target.get();
                if (PunishmentType.MUTE_TYPES.contains(type) && cache.activeMute(targetPlayer.getUniqueId()).isPresent()) {
                    source.sendRichMessage(lang.get("punish.already-muted", targetPlayer.getUsername()));
                    return;
                }
                String reason = args.length > 1
                        ? String.join(" ", Arrays.copyOfRange(args, 1, args.length))
                        : lang.get("punish.no-reason");
                UUID staffUuid = source instanceof Player p ? p.getUniqueId() : null;

                IssuePunishmentRequest req = new IssuePunishmentRequest(targetPlayer.getUniqueId().toString(),
                        targetPlayer.getUsername(), type, reason, staffUuid == null ? null : staffUuid.toString(),
                        null, false);
                Optional<PunishmentDto> result = client.issue(req);
                if (result.isPresent()) {
                    PunishmentDto dto = result.get();
                    source.sendRichMessage(lang.get("punish.issued", dto.type(), targetPlayer.getUsername(), dto.id(), dto.reason()));
                } else {
                    source.sendRichMessage(lang.get("punish.queued", type, targetPlayer.getUsername()));
                }
            }
        };
    }
}
