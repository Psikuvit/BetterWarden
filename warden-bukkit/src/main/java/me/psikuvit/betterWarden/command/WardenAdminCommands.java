package me.psikuvit.betterWarden.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.psikuvit.betterWarden.core.config.ConfigBootstrap;
import me.psikuvit.betterWarden.core.config.CoreConfig;
import me.psikuvit.betterWarden.core.config.EditionService;
import me.psikuvit.betterWarden.core.discord.DiscordBotService;
import me.psikuvit.betterWarden.core.discord.DiscordLinkService;
import me.psikuvit.betterWarden.core.model.Escalation;
import me.psikuvit.betterWarden.core.model.PunishmentTemplate;
import me.psikuvit.betterWarden.core.model.PunishmentType;
import me.psikuvit.betterWarden.core.panel.setup.SetupCodeService;
import me.psikuvit.betterWarden.core.repo.PanelUserRepository;
import me.psikuvit.betterWarden.core.service.EscalationService;
import me.psikuvit.betterWarden.core.service.LangService;
import me.psikuvit.betterWarden.core.service.PunishmentTemplateService;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;

/** /warden ... - admin/ops subcommands. Each feature area registers its own subtree; Brigadier merges them under one "warden" root. */
public final class WardenAdminCommands {

    private static final String PERMISSION = "warden.admin";

    private final JavaPlugin plugin;
    private final PunishmentTemplateService templates;
    private final EscalationService escalationService;
    private final CoreConfig config;
    private final File configFile;
    private final LangService lang;
    private final SetupCodeService setupCodeService;
    private final PanelUserRepository panelUsers;
    private final DiscordLinkService linkService;
    private final EditionService edition;
    private final DiscordBotService discordBot;

    private WardenAdminCommands(JavaPlugin plugin, PunishmentTemplateService templates, EscalationService escalationService,
                                 CoreConfig config, File configFile, LangService lang,
                                 SetupCodeService setupCodeService, PanelUserRepository panelUsers, DiscordLinkService linkService,
                                 EditionService edition, DiscordBotService discordBot) {
        this.plugin = plugin;
        this.templates = templates;
        this.escalationService = escalationService;
        this.config = config;
        this.configFile = configFile;
        this.lang = lang;
        this.setupCodeService = setupCodeService;
        this.panelUsers = panelUsers;
        this.linkService = linkService;
        this.edition = edition;
        this.discordBot = discordBot;
    }

    public static void register(JavaPlugin plugin, PunishmentTemplateService templates, EscalationService escalationService,
                                 CoreConfig config, File configFile, LangService lang,
                                 SetupCodeService setupCodeService, PanelUserRepository panelUsers, DiscordLinkService linkService,
                                 EditionService edition, DiscordBotService discordBot) {
        WardenAdminCommands commands = new WardenAdminCommands(plugin, templates, escalationService, config, configFile, lang,
                setupCodeService, panelUsers, linkService, edition, discordBot);
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands registrar = event.registrar();
            var root = literal("warden")
                    .requires(src -> src.getSender().hasPermission(PERMISSION))
                    .then(literal("status")
                            .executes(commands::executeStatus))
                    .then(literal("reload")
                            .executes(commands::executeReload))
                    .then(literal("debug")
                            .executes(commands::executeDebug));

            // docs/spec/08-TIERS-AND-LICENSING.txt - templates/escalation, the panel setup wizard,
            // and Discord-link force-unlink are all paid-only features (the systems they configure
            // don't exist in the free edition), so their command subtrees aren't registered at all.
            if (edition.isPaid()) {
                root.then(literal("template")
                                .then(literal("add")
                                        .then(argument("key", StringArgumentType.word())
                                                .then(argument("type", StringArgumentType.word())
                                                        .then(argument("duration", StringArgumentType.word())
                                                                .then(argument("group", StringArgumentType.word())
                                                                        .then(argument("reason", StringArgumentType.greedyString())
                                                                                .executes(commands::executeAdd)))))))
                                .then(literal("remove")
                                        .then(argument("key", StringArgumentType.word())
                                                .executes(commands::executeRemove)))
                                .then(literal("list")
                                        .executes(commands::executeList)))
                        .then(literal("escalation")
                                .then(literal("add")
                                        .then(argument("group", StringArgumentType.word())
                                                .then(argument("offence", IntegerArgumentType.integer(1))
                                                        .then(argument("type", StringArgumentType.word())
                                                                .then(argument("duration", StringArgumentType.word())
                                                                        .executes(commands::executeEscalationAdd))))))
                                .then(literal("list")
                                        .then(argument("group", StringArgumentType.word())
                                                .executes(commands::executeEscalationList))))
                        .then(literal("setup")
                                .executes(commands::executeSetup))
                        .then(literal("unlink")
                                .then(argument("player", StringArgumentType.word())
                                        .executes(commands::executeForceUnlink)));
            }

            registrar.register(root.build(), "BetterWarden admin commands");
        });
    }

    private int executeStatus(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Msg.send(sender, lang.get("admin.status-header"));
        Msg.send(sender, lang.get("admin.status-version", plugin.getPluginMeta().getVersion()));
        Msg.send(sender, lang.get("admin.status-edition", edition.isPaid() ? "Standard/Network" : "Free"));
        Msg.send(sender, lang.get("admin.status-server", config.getServerName()));
        Msg.send(sender, lang.get("admin.status-mode"));
        Msg.send(sender, lang.get("admin.status-storage", config.getStorage().getType()));
        Msg.send(sender, lang.get("admin.status-login-gate", config.getLoginGate().isFailOpen()));
        try {
            int count = templates.list().size();
            Msg.send(sender, lang.get("admin.status-db-ok", count));
        } catch (Exception e) {
            Msg.send(sender, lang.get("admin.status-db-unreachable", e.getMessage()));
        }

        if (edition.isFree()) {
            Msg.send(sender, lang.get("admin.status-discord-paid-only"));
            Msg.send(sender, lang.get("admin.status-panel-paid-only"));
        } else {
            if (!config.getDiscord().isEnabled() || config.getDiscord().getToken().isBlank()) {
                Msg.send(sender, lang.get("admin.status-discord-disabled"));
            } else {
                Msg.send(sender, lang.get(discordBot.isConnected() ? "admin.status-discord-connected" : "admin.status-discord-not-connected"));
            }
            if (panelUsers.count() == 0) {
                Msg.send(sender, lang.get("admin.status-panel-no-owner"));
            } else {
                Msg.send(sender, lang.get("admin.status-panel-ready", panelUsers.count()));
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    private int executeReload(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        try {
            ConfigBootstrap.validate(configFile);
            Msg.send(sender, lang.get("admin.reload-ok"));
            Msg.send(sender, lang.get("admin.reload-note"));
        } catch (Exception e) {
            Msg.send(sender, lang.get("admin.reload-failed", e.getMessage()));
        }
        return Command.SINGLE_SUCCESS;
    }

    /** No paste-service upload - writes a local file under the plugin's data folder and reports the path. */
    private int executeDebug(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();

        StringBuilder report = new StringBuilder();
        report.append("BetterWarden debug report\n");
        report.append("Generated: ").append(Instant.now()).append('\n');
        report.append("Plugin version: ").append(plugin.getPluginMeta().getVersion()).append('\n');
        report.append("Edition: ").append(edition.isPaid() ? "Standard/Network" : "Free").append('\n');
        report.append("Server: ").append(Bukkit.getVersion()).append('\n');
        report.append("Bukkit API: ").append(Bukkit.getBukkitVersion()).append('\n');
        report.append("Java: ").append(System.getProperty("java.version")).append('\n');
        report.append("OS: ").append(System.getProperty("os.name")).append(' ').append(System.getProperty("os.arch")).append('\n');
        report.append("Storage type: ").append(config.getStorage().getType()).append('\n');
        report.append("Login gate fail-open: ").append(config.getLoginGate().isFailOpen()).append('\n');
        report.append("Config hash (SHA-256): ").append(configHash()).append('\n');
        try {
            int count = templates.list().size();
            report.append("Database: OK (").append(count).append(" punishment template(s))\n");
        } catch (Exception e) {
            report.append("Database: UNREACHABLE - ").append(e.getMessage()).append('\n');
        }
        if (edition.isFree()) {
            report.append("Discord bot: Standard/Network feature\n");
            report.append("Panel: Standard/Network feature\n");
        } else {
            if (!config.getDiscord().isEnabled() || config.getDiscord().getToken().isBlank()) {
                report.append("Discord bot: disabled (no token configured)\n");
            } else {
                report.append("Discord bot: ").append(discordBot.isConnected() ? "connected" : "enabled but NOT connected - check the token").append('\n');
            }
            report.append("Panel: ").append(panelUsers.count() == 0 ? "no owner account yet" : panelUsers.count() + " staff account(s)").append('\n');
        }

        File debugDir = new File(plugin.getDataFolder(), "debug");
        String filename = "debug-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now()) + ".txt";
        try {
            if (!debugDir.exists() && !debugDir.mkdirs()) {
                throw new IOException("Could not create " + debugDir);
            }
            File outFile = new File(debugDir, filename);
            Files.writeString(outFile.toPath(), report, StandardCharsets.UTF_8);
            Msg.send(sender, lang.get("admin.debug-written", filename));
        } catch (IOException e) {
            Msg.send(sender, lang.get("admin.debug-write-failed", e.getMessage()));
            sender.sendMessage(report.toString());
        }
        return Command.SINGLE_SUCCESS;
    }

    /** docs/spec/04-PANEL.txt SS2: "/warden setup in-game reissues the code." Only meaningful in HOST mode - CLIENT backends have no local panel/setup flow of their own. */
    private int executeSetup(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (panelUsers.count() > 0) {
            Msg.send(sender, lang.get("admin.setup-already-done"));
            return 0;
        }
        String code = setupCodeService.generate();
        Msg.send(sender, lang.get("admin.setup-code-header"));
        Msg.send(sender, lang.get("admin.setup-code-url", config.getPanel().getPort()));
        Msg.send(sender, lang.get("admin.setup-code-value", code));
        return Command.SINGLE_SUCCESS;
    }

    /** docs/spec/05-DISCORD-BOT.txt §6 "admin force-unlink". Fast-path resolution only (online or Bukkit-cached) - a player who linked their Discord account was necessarily online at some point. */
    private int executeForceUnlink(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String name = StringArgumentType.getString(ctx, "player");
        var target = TargetResolver.resolveCached(name);
        if (target.isEmpty()) {
            Msg.send(sender, lang.get("common.player-not-found"));
            return 0;
        }
        boolean removed = linkService.unlinkMinecraft(target.get().uuid());
        Msg.send(sender, lang.get(removed ? "link.force-unlinked" : "link.force-unlink-not-found", target.get().name()));
        return Command.SINGLE_SUCCESS;
    }

    private String configHash() {
        try {
            byte[] bytes = Files.readAllBytes(configFile.toPath());
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return "unavailable (" + e.getMessage() + ")";
        }
    }

    private int executeAdd(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String key = StringArgumentType.getString(ctx, "key");
        String typeStr = StringArgumentType.getString(ctx, "type");
        String duration = StringArgumentType.getString(ctx, "duration");
        String group = StringArgumentType.getString(ctx, "group");
        String reason = StringArgumentType.getString(ctx, "reason");

        PunishmentType type;
        try {
            type = PunishmentType.valueOf(typeStr.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            Msg.send(sender, lang.get("admin.unknown-type", typeStr));
            return 0;
        }
        if (isNone(duration)) {
            duration = null;
        }
        if (isNone(group)) {
            group = null;
        }

        PunishmentTemplate template = templates.create(key, key, type, duration, reason, group);
        Msg.send(sender, lang.get("admin.template-created", template.getKey(), type, duration == null ? "perm" : duration,
                group == null ? "" : ", escalation group " + group));
        return Command.SINGLE_SUCCESS;
    }

    private int executeEscalationAdd(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String group = StringArgumentType.getString(ctx, "group");
        int offence = IntegerArgumentType.getInteger(ctx, "offence");
        String typeStr = StringArgumentType.getString(ctx, "type");
        String duration = StringArgumentType.getString(ctx, "duration");

        PunishmentType type;
        try {
            type = PunishmentType.valueOf(typeStr.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            Msg.send(sender, lang.get("admin.unknown-type", typeStr));
            return 0;
        }
        if (isNone(duration)) {
            duration = null;
        }

        escalationService.addRung(group, offence, type, duration);
        Msg.send(sender, lang.get("admin.escalation-added", group, offence, type, duration == null ? "perm" : duration));
        return Command.SINGLE_SUCCESS;
    }

    private int executeEscalationList(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String group = StringArgumentType.getString(ctx, "group");
        List<Escalation> rungs = escalationService.list(group);
        if (rungs.isEmpty()) {
            Msg.send(sender, lang.get("admin.escalation-list-empty", group));
            return Command.SINGLE_SUCCESS;
        }
        Msg.send(sender, lang.get("admin.escalation-list-header", group));
        for (Escalation rung : rungs) {
            Msg.send(sender, lang.get("admin.escalation-list-line", rung.getOffenceNumber(), rung.getType(),
                    rung.getDuration() == null ? "perm" : rung.getDuration()));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static boolean isNone(String value) {
        return "-".equals(value) || "none".equalsIgnoreCase(value);
    }

    private int executeRemove(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String key = StringArgumentType.getString(ctx, "key");
        if (templates.delete(key)) {
            Msg.send(sender, lang.get("admin.template-removed", key));
        } else {
            Msg.send(sender, lang.get("admin.template-not-found", key));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int executeList(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        List<PunishmentTemplate> all = templates.list();
        if (all.isEmpty()) {
            Msg.send(sender, lang.get("admin.template-list-empty"));
            return Command.SINGLE_SUCCESS;
        }
        Msg.send(sender, lang.get("admin.template-list-header"));
        for (PunishmentTemplate t : all) {
            Msg.send(sender, lang.get("admin.template-list-line", t.getKey(), t.getType(),
                    t.getDuration() == null ? "perm" : t.getDuration(), t.getReason()));
        }
        return Command.SINGLE_SUCCESS;
    }
}
