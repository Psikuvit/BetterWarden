package me.psikuvit.betterWarden.command;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import me.psikuvit.betterWarden.core.service.LangService;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * {@code /wardennet ...} on the proxy side: "panel" and "nodes". Deliberately NOT named "warden" -
 * a Velocity/BungeeCord proxy command registration always wins over a same-named backend command
 * for any player connecting through it (the proxy claims the whole literal and either handles it
 * itself or doesn't forward it at all), so naming this "warden" would silently swallow every
 * backend server's own richer /warden (status/reload/debug/template/escalation/unlink -
 * WardenAdminCommands) the moment a proxy sits in front of it - a real bug this project hit
 * (see commit history/PLAN.md), not a hypothetical. Takes panelUrl/nodeCount as suppliers rather
 * than a CoreConfig/NodeWebSocketHandler directly, so the same command works whether this proxy is
 * HOST (embeds its own core) or CLIENT (talks to an external one over RemoteCoreClient, which has
 * no local node hub of its own to ask directly).
 */
public final class WardenProxyCommands {

    private static final String PERMISSION = "warden.admin";

    public static void register(Object plugin, ProxyServer server, LangService lang, Supplier<String> panelUrl,
                                 Supplier<Optional<Integer>> nodeCount, Supplier<String> setupCode) {
        CommandManager manager = server.getCommandManager();

        manager.register(manager.metaBuilder("wardennet").plugin(plugin).build(), new SimpleCommand() {
            @Override
            public boolean hasPermission(Invocation invocation) {
                return invocation.source().hasPermission(PERMISSION);
            }

            @Override
            public void execute(Invocation invocation) {
                CommandSource source = invocation.source();
                String[] args = invocation.arguments();
                if (args.length == 0) {
                    source.sendRichMessage("<gray>Usage: /wardennet <panel|nodes|setup>");
                    return;
                }
                switch (args[0].toLowerCase(Locale.ROOT)) {
                    case "panel" -> source.sendRichMessage(lang.get("admin.panel-url", panelUrl.get()));
                    // No per-node identity yet (shared node-token, see NodeAuthInterceptor) - just
                    // a live connection count until NodeRegistry tracks who's actually connected.
                    case "nodes" -> nodeCount.get().ifPresentOrElse(
                            count -> source.sendRichMessage(lang.get("admin.nodes-connected", count)),
                            () -> source.sendRichMessage("<red>Could not reach Core for the node count."));
                    case "setup" -> source.sendRichMessage(setupCode.get());
                    default -> source.sendRichMessage("<gray>Usage: /wardennet <panel|nodes|setup>");
                }
            }
        });
    }
}
