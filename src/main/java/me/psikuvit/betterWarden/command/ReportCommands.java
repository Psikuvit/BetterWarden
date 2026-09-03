package me.psikuvit.betterWarden.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.psikuvit.betterWarden.core.model.Report;
import me.psikuvit.betterWarden.core.model.ReportStatus;
import me.psikuvit.betterWarden.core.service.LangService;
import me.psikuvit.betterWarden.core.service.PlayerTrackingService;
import me.psikuvit.betterWarden.core.service.ReportService;
import me.psikuvit.betterWarden.gui.ReportQueueMenu;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;

public final class ReportCommands {

    private final ReportService service;
    private final PlayerTrackingService playerTracking;
    private final LangService lang;

    private ReportCommands(ReportService service, PlayerTrackingService playerTracking, LangService lang) {
        this.service = service;
        this.playerTracking = playerTracking;
        this.lang = lang;
    }

    public static void register(JavaPlugin plugin, ReportService service, PlayerTrackingService playerTracking, LangService lang) {
        ReportCommands commands = new ReportCommands(service, playerTracking, lang);
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands registrar = event.registrar();
            registrar.register(commands.report().build(), "Report a player to staff");
            registrar.register(commands.reports().build(), "Staff report queue");
        });
    }

    private LiteralArgumentBuilder<CommandSourceStack> report() {
        return literal("report")
                .then(argument("player", StringArgumentType.word())
                        .suggests(PlayerSuggestions.ONLINE_PLAYERS)
                        .executes(this::executeReport)
                        .then(argument("reason", StringArgumentType.greedyString())
                                .executes(this::executeReport)));
    }

    private LiteralArgumentBuilder<CommandSourceStack> reports() {
        return literal("reports")
                .requires(src -> src.getSender().hasPermission("warden.reports"))
                .executes(ctx -> executeList(ctx, ReportStatus.OPEN))
                .then(literal("open").executes(ctx -> executeList(ctx, ReportStatus.OPEN)))
                .then(literal("claimed").executes(ctx -> executeList(ctx, ReportStatus.CLAIMED)))
                .then(literal("all").executes(ctx -> executeList(ctx, null)))
                .then(literal("view").then(argument("id", IntegerArgumentType.integer(1)).executes(this::executeView)))
                .then(literal("claim").then(argument("id", IntegerArgumentType.integer(1)).executes(this::executeClaim)))
                .then(literal("dismiss").then(argument("id", IntegerArgumentType.integer(1)).executes(this::executeDismiss)))
                .then(literal("close").then(argument("id", IntegerArgumentType.integer(1)).executes(this::executeClose)));
    }

    private int executeReport(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Optional<TargetResolver.Target> target = TargetResolver.resolve(StringArgumentType.getString(ctx, "player"));
        if (target.isEmpty()) {
            Msg.send(sender, lang.get("common.player-not-found"));
            return 0;
        }
        if (!(sender instanceof Player reporter)) {
            // Console reports are allowed conceptually, but there's no reporter identity to attribute - keep it simple.
            Msg.send(sender, lang.get("common.player-not-found"));
            return 0;
        }
        if (reporter.getUniqueId().equals(target.get().uuid())) {
            Msg.send(sender, lang.get("report.cant-report-self"));
            return 0;
        }
        String reason = reasonOrDefault(ctx);
        Player targetPlayer = Bukkit.getPlayer(target.get().uuid());
        String location = targetPlayer == null ? null : formatLocation(targetPlayer);

        Report report = service.create(reporter.getUniqueId(), target.get().uuid(), reason, null, location, null);
        Msg.send(sender, lang.get("report.submitted", report.getId(), target.get().name()));
        return Command.SINGLE_SUCCESS;
    }

    private int executeList(CommandContext<CommandSourceStack> ctx, ReportStatus status) {
        CommandSender sender = ctx.getSource().getSender();

        if (sender instanceof Player staff) {
            new ReportQueueMenu(service, playerTracking, status).open(staff);
            return Command.SINGLE_SUCCESS;
        }

        List<Report> reports = service.list(status);
        if (reports.isEmpty()) {
            Msg.send(sender, lang.get("report.list-empty"));
            return Command.SINGLE_SUCCESS;
        }
        Msg.send(sender, lang.get("report.list-header"));
        for (Report r : reports) {
            Msg.send(sender, lang.get("report.list-line", r.getId(), r.getStatus(), r.getTarget(), r.getReporter(), r.getReason()));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int executeView(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        long id = IntegerArgumentType.getInteger(ctx, "id");
        Optional<Report> found = service.find(id);
        if (found.isEmpty()) {
            Msg.send(sender, lang.get("report.not-found", id));
            return 0;
        }
        Report r = found.get();
        Msg.send(sender, lang.get("report.detail-header", r.getId()));
        Msg.send(sender, lang.get("report.detail-target", r.getTarget()));
        Msg.send(sender, lang.get("report.detail-reporter", r.getReporter()));
        Msg.send(sender, lang.get("report.detail-status", r.getStatus()));
        Msg.send(sender, lang.get("report.detail-reason", r.getReason()));
        if (r.getLocation() != null) {
            Msg.send(sender, lang.get("report.detail-location", r.getLocation()));
        }
        Msg.send(sender, lang.get("report.detail-chat-header"));
        String snapshot = r.getChatSnapshot();
        if (snapshot == null || snapshot.isBlank()) {
            Msg.send(sender, lang.get("report.detail-chat-empty"));
        } else {
            for (String line : snapshot.split("\n")) {
                sender.sendMessage(line);
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    private int executeClaim(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        long id = IntegerArgumentType.getInteger(ctx, "id");
        UUID staffUuid = sender instanceof Player p ? p.getUniqueId() : null;
        if (service.claim(id, staffUuid).isEmpty()) {
            Msg.send(sender, lang.get("report.not-found", id));
            return 0;
        }
        Msg.send(sender, lang.get("report.claimed", id));
        return Command.SINGLE_SUCCESS;
    }

    private int executeDismiss(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        long id = IntegerArgumentType.getInteger(ctx, "id");
        UUID staffUuid = sender instanceof Player p ? p.getUniqueId() : null;
        if (service.dismiss(id, staffUuid).isEmpty()) {
            Msg.send(sender, lang.get("report.not-found", id));
            return 0;
        }
        Msg.send(sender, lang.get("report.dismissed", id));
        return Command.SINGLE_SUCCESS;
    }

    private int executeClose(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        long id = IntegerArgumentType.getInteger(ctx, "id");
        UUID staffUuid = sender instanceof Player p ? p.getUniqueId() : null;
        if (service.close(id, staffUuid).isEmpty()) {
            Msg.send(sender, lang.get("report.not-found", id));
            return 0;
        }
        Msg.send(sender, lang.get("report.closed", id));
        return Command.SINGLE_SUCCESS;
    }

    private String reasonOrDefault(CommandContext<CommandSourceStack> ctx) {
        try {
            return StringArgumentType.getString(ctx, "reason");
        } catch (IllegalArgumentException e) {
            return lang.get("report.no-reason");
        }
    }

    private static String formatLocation(Player player) {
        var loc = player.getLocation();
        return String.format(Locale.ROOT, "%s,%.1f,%.1f,%.1f",
                loc.getWorld() != null ? loc.getWorld().getName() : "unknown", loc.getX(), loc.getY(), loc.getZ());
    }
}
