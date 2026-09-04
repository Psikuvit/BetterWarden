package me.psikuvit.betterWarden.command;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import me.psikuvit.betterWarden.core.config.CoreConfig;
import me.psikuvit.betterWarden.core.service.LangService;

/** {@code /warden ...} on the proxy side. Only "panel" so far - "nodes" needs NodeRegistry, which doesn't exist yet (PLAN.md Stage 3). */
public final class WardenProxyCommands {

    private static final String PERMISSION = "warden.admin";

    public static void register(ProxyServer server, CoreConfig config, LangService lang) {
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
                if (args.length == 0 || !"panel".equalsIgnoreCase(args[0])) {
                    source.sendRichMessage("<gray>Usage: /warden panel");
                    return;
                }
                String panelUrl = "http://" + config.getNode().getAdvertiseHost() + ":" + config.getPanel().getPort();
                source.sendRichMessage(lang.get("admin.panel-url", panelUrl));
            }
        });
    }
}
