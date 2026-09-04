package me.psikuvit.betterWarden.command;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import me.psikuvit.betterWarden.core.config.CoreConfig;
import me.psikuvit.betterWarden.core.service.LangService;
import me.psikuvit.betterWarden.core.ws.NodeWebSocketHandler;

import java.util.Locale;

/** {@code /warden ...} on the proxy side: "panel" and "nodes". */
public final class WardenProxyCommands {

    private static final String PERMISSION = "warden.admin";

    public static void register(ProxyServer server, CoreConfig config, LangService lang, NodeWebSocketHandler nodeHub) {
        CommandManager manager = server.getCommandManager();
        manager.register(manager.metaBuilder("warden").plugin(WardenProxyCommands.class).build(), new SimpleCommand() {
            @Override
            public boolean hasPermission(Invocation invocation) {
                return invocation.source().hasPermission(PERMISSION);
            }

            @Override
            public void execute(Invocation invocation) {
                CommandSource source = invocation.source();
                String[] args = invocation.arguments();
                if (args.length == 0) {
                    source.sendRichMessage("<gray>Usage: /warden <panel|nodes>");
                    return;
                }
                switch (args[0].toLowerCase(Locale.ROOT)) {
                    case "panel" -> {
                        String panelUrl = "http://" + config.getNode().getAdvertiseHost() + ":" + config.getPanel().getPort();
                        source.sendRichMessage(lang.get("admin.panel-url", panelUrl));
                    }
                    // No per-node identity yet (shared node-token, see NodeAuthInterceptor) - just
                    // a live connection count until NodeRegistry tracks who's actually connected.
                    case "nodes" -> source.sendRichMessage(lang.get("admin.nodes-connected", nodeHub.connectedCount()));
                    default -> source.sendRichMessage("<gray>Usage: /warden <panel|nodes>");
                }
            }
        });
    }
}
