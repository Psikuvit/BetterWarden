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

    private WardenAdminCommands(JavaPlugin plugin, PunishmentTemplateService templates, EscalationService escalationService,
                                 CoreConfig config, File configFile, LangService lang,
                                 SetupCodeService setupCodeService, PanelUserRepository panelUsers) {
        this.plugin = plugin;
        this.templates = templates;
        this.escalationService = escalationService;
        this.config = config;
        this.configFile = configFile;
        this.lang = lang;
        this.setupCodeService = setupCodeService;
        this.panelUsers = panelUsers;
    }

    public static void register(JavaPlugin plugin, PunishmentTemplateService templates, EscalationService escalationService,
                                 CoreConfig config, File configFile, LangService lang,
                                 SetupCodeService setupCodeService, PanelUserRepository panelUsers) {
        WardenAdminCommands commands = new WardenAdminCommands(plugin, templates, escalationService, config, configFile, lang,
                setupCodeService, panelUsers);
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands registrar = event.registrar();
            registrar.register(
                    literal("warden")
                            .requires(src -> src.getSender().hasPermission(PERMISSION))
                            .then(literal("template")
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
                            .then(literal("status")
                                    .executes(commands::executeStatus))
                            .then(literal("reload")
                                    .executes(commands::executeReload))
                            .then(literal("debug")
                                    .executes(commands::executeDebug))
                            .then(literal("setup")
                                    .executes(commands::executeSetup))
                            .build(),
                    "BetterWarden admin commands");
        });
    }

    private int executeStatus(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Msg.send(sender, lang.get("admin.status-header"));
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
            return Command.SINGLE_SUCCESS;
        }
        String code = setupCodeService.generate();
        Msg.send(sender, lang.get("admin.setup-code-header"));
        Msg.send(sender, lang.get("admin.setup-code-url", config.getPanel().getPort()));
        Msg.send(sender, lang.get("admin.setup-code-value", code));
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
