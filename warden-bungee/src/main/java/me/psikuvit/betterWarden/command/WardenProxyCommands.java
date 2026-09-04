package me.psikuvit.betterWarden.command;

import me.psikuvit.betterWarden.core.service.LangService;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Plugin;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * {@code /warden ...} on the proxy side: "panel", "nodes" and "setup" - same shape as
 * warden-velocity's own WardenProxyCommands, ported to a plain BungeeCord Command.
 */
public final class WardenProxyCommands {

    private static final String PERMISSION = "warden.admin";

    public static void register(Plugin plugin, ProxyServer server, LangService lang, BungeeMsg msg,
                                 Supplier<String> panelUrl, Supplier<Optional<Integer>> nodeCount, Supplier<String> setupCode) {
        server.getPluginManager().registerCommand(plugin, new Command("warden", PERMISSION) {
            @Override
            public void execute(CommandSender sender, String[] args) {
                if (args.length == 0) {
                    msg.send(sender, "<gray>Usage: /warden <panel|nodes|setup>");
                    return;
                }
                switch (args[0].toLowerCase(Locale.ROOT)) {
                    case "panel" -> msg.send(sender, lang.get("admin.panel-url", panelUrl.get()));
                    // No per-node identity yet (shared node-token, see NodeAuthInterceptor) - just
                    // a live connection count until NodeRegistry tracks who's actually connected.
                    case "nodes" -> nodeCount.get().ifPresentOrElse(
                            count -> msg.send(sender, lang.get("admin.nodes-connected", count)),
                            () -> msg.send(sender, "<red>Could not reach Core for the node count."));
                    case "setup" -> msg.send(sender, setupCode.get());
                    default -> msg.send(sender, "<gray>Usage: /warden <panel|nodes|setup>");
                }
            }
        });
    }
}
