package me.psikuvit.betterWarden.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.psikuvit.betterWarden.core.config.ConfigBootstrap;
import me.psikuvit.betterWarden.core.config.CoreConfig;
import me.psikuvit.betterWarden.core.model.PunishmentTemplate;
import me.psikuvit.betterWarden.core.model.PunishmentType;
import me.psikuvit.betterWarden.core.service.PunishmentTemplateService;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;
import java.util.Locale;

import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;

/** /warden ... - admin/ops subcommands. Each feature area registers its own subtree; Brigadier merges them under one "warden" root. */
public final class WardenAdminCommands {

    private static final String PERMISSION = "warden.admin";

    private final PunishmentTemplateService templates;
    private final CoreConfig config;
    private final File configFile;

    private WardenAdminCommands(PunishmentTemplateService templates, CoreConfig config, File configFile) {
        this.templates = templates;
        this.config = config;
        this.configFile = configFile;
    }

    public static void register(JavaPlugin plugin, PunishmentTemplateService templates, CoreConfig config, File configFile) {
        WardenAdminCommands commands = new WardenAdminCommands(templates, config, configFile);
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
                                                                    .then(argument("reason", StringArgumentType.greedyString())
                                                                            .executes(commands::executeAdd))))))
                                    .then(literal("remove")
                                            .then(argument("key", StringArgumentType.word())
                                                    .executes(commands::executeRemove)))
                                    .then(literal("list")
                                            .executes(commands::executeList)))
                            .then(literal("status")
                                    .executes(commands::executeStatus))
                            .then(literal("reload")
                                    .executes(commands::executeReload))
                            .build(),
                    "BetterWarden admin commands");
        });
    }

    private int executeStatus(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        sender.sendMessage("=== BetterWarden status ===");
        sender.sendMessage("Server: " + config.getServerName());
        sender.sendMessage("Mode: HOST (single server, no proxy)");
        sender.sendMessage("Storage: " + config.getStorage().getType());
        sender.sendMessage("Login gate fail-open: " + config.getLoginGate().isFailOpen());
        try {
            int count = templates.list().size();
            sender.sendMessage("Database: OK (" + count + " punishment template(s))");
        } catch (Exception e) {
            sender.sendMessage("Database: UNREACHABLE - " + e.getMessage());
        }
        return Command.SINGLE_SUCCESS;
    }

    private int executeReload(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        try {
            ConfigBootstrap.validate(configFile);
            sender.sendMessage("config.yml re-read successfully.");
            sender.sendMessage("Note: storage/port/Flyway settings only take effect on a full restart.");
        } catch (Exception e) {
            sender.sendMessage("config.yml failed to parse: " + e.getMessage());
        }
        return Command.SINGLE_SUCCESS;
    }

    private int executeAdd(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String key = StringArgumentType.getString(ctx, "key");
        String typeStr = StringArgumentType.getString(ctx, "type");
        String duration = StringArgumentType.getString(ctx, "duration");
        String reason = StringArgumentType.getString(ctx, "reason");

        PunishmentType type;
        try {
            type = PunishmentType.valueOf(typeStr.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            sender.sendMessage("Unknown punishment type: " + typeStr);
            return 0;
        }
        if ("-".equals(duration) || "none".equalsIgnoreCase(duration)) {
            duration = null;
        }

        PunishmentTemplate template = templates.create(key, key, type, duration, reason);
        sender.sendMessage("Template #" + template.getKey() + " created (" + type + ", " + (duration == null ? "perm" : duration) + ").");
        return Command.SINGLE_SUCCESS;
    }

    private int executeRemove(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String key = StringArgumentType.getString(ctx, "key");
        if (templates.delete(key)) {
            sender.sendMessage("Template #" + key + " removed.");
        } else {
            sender.sendMessage("No template found for #" + key);
        }
        return Command.SINGLE_SUCCESS;
    }

    private int executeList(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        List<PunishmentTemplate> all = templates.list();
        if (all.isEmpty()) {
            sender.sendMessage("No punishment templates configured.");
            return Command.SINGLE_SUCCESS;
        }
        sender.sendMessage("Punishment templates:");
        for (PunishmentTemplate t : all) {
            sender.sendMessage("#" + t.getKey() + " " + t.getType() + " [" + (t.getDuration() == null ? "perm" : t.getDuration()) + "] " + t.getReason());
        }
        return Command.SINGLE_SUCCESS;
    }
}
