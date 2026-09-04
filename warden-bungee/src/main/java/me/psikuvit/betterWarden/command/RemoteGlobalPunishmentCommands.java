package me.psikuvit.betterWarden.command;

import me.psikuvit.betterWarden.core.client.RemoteCoreClient;
import me.psikuvit.betterWarden.core.client.RemotePunishmentCache;
import me.psikuvit.betterWarden.core.model.PunishmentType;
import me.psikuvit.betterWarden.core.network.dto.IssuePunishmentRequest;
import me.psikuvit.betterWarden.core.network.dto.PunishmentDto;
import me.psikuvit.betterWarden.core.service.LangService;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Plugin;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

/**
 * CLIENT-mode /gban /gmute /gkick - issues over RemoteCoreClient instead of a local
 * PunishmentService. Scoped down like warden-velocity's own RemoteGlobalPunishmentCommands:
 * online players only, no offline fallback (needs a player-lookup REST endpoint that doesn't exist yet).
 */
public final class RemoteGlobalPunishmentCommands {

    private final ProxyServer server;
    private final RemoteCoreClient client;
    private final RemotePunishmentCache cache;
    private final LangService lang;
    private final BungeeMsg msg;

    private RemoteGlobalPunishmentCommands(ProxyServer server, RemoteCoreClient client,
                                            RemotePunishmentCache cache, LangService lang, BungeeMsg msg) {
        this.server = server;
        this.client = client;
        this.cache = cache;
        this.lang = lang;
        this.msg = msg;
    }

    public static void register(Plugin plugin, ProxyServer server, RemoteCoreClient client,
                                 RemotePunishmentCache cache, LangService lang, BungeeMsg msg) {
        RemoteGlobalPunishmentCommands commands = new RemoteGlobalPunishmentCommands(server, client, cache, lang, msg);
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
                ProxiedPlayer target = server.getPlayer(args[0]);
                if (target == null) {
                    msg.send(sender, lang.get("common.player-not-found"));
                    return;
                }
                if (PunishmentType.MUTE_TYPES.contains(type) && cache.activeMute(target.getUniqueId()).isPresent()) {
                    msg.send(sender, lang.get("punish.already-muted", target.getName()));
                    return;
                }
                String reason = args.length > 1
                        ? String.join(" ", Arrays.copyOfRange(args, 1, args.length))
                        : lang.get("punish.no-reason");
                UUID staffUuid = sender instanceof ProxiedPlayer p ? p.getUniqueId() : null;

                IssuePunishmentRequest req = new IssuePunishmentRequest(target.getUniqueId().toString(),
                        target.getName(), type, reason, staffUuid == null ? null : staffUuid.toString(),
                        null, false);
                Optional<PunishmentDto> result = client.issue(req);
                if (result.isPresent()) {
                    PunishmentDto dto = result.get();
                    msg.send(sender, lang.get("punish.issued", dto.type(), target.getName(), dto.id(), dto.reason()));
                } else {
                    msg.send(sender, lang.get("punish.queued", type, target.getName()));
                }
            }
        };
    }
}
