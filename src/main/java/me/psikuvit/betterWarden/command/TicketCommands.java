package me.psikuvit.betterWarden.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.psikuvit.betterWarden.core.model.Ticket;
import me.psikuvit.betterWarden.core.model.TicketMessage;
import me.psikuvit.betterWarden.core.model.TicketStatus;
import me.psikuvit.betterWarden.core.service.LangService;
import me.psikuvit.betterWarden.core.service.TicketService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;

public final class TicketCommands {

    private final TicketService service;
    private final LangService lang;

    private TicketCommands(TicketService service, LangService lang) {
        this.service = service;
        this.lang = lang;
    }

    public static void register(JavaPlugin plugin, TicketService service, LangService lang) {
        TicketCommands commands = new TicketCommands(service, lang);
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands registrar = event.registrar();
            registrar.register(commands.ticket().build(), "Open a support ticket");
            registrar.register(commands.tickets().build(), "Staff ticket inbox");
        });
    }

    private LiteralArgumentBuilder<CommandSourceStack> ticket() {
        return literal("ticket")
                .then(argument("subject", StringArgumentType.greedyString())
                        .executes(this::executeOpen));
    }

    private LiteralArgumentBuilder<CommandSourceStack> tickets() {
        return literal("tickets")
                .requires(src -> src.getSender().hasPermission("warden.tickets"))
                .executes(ctx -> executeList(ctx, TicketStatus.OPEN))
                .then(literal("open").executes(ctx -> executeList(ctx, TicketStatus.OPEN)))
                .then(literal("all").executes(ctx -> executeList(ctx, null)))
                .then(literal("mine").executes(this::executeMine))
                .then(literal("view").then(argument("id", IntegerArgumentType.integer(1)).executes(this::executeView)))
                .then(literal("reply").then(argument("id", IntegerArgumentType.integer(1))
                        .then(argument("text", StringArgumentType.greedyString()).executes(this::executeReply))))
                .then(literal("note").then(argument("id", IntegerArgumentType.integer(1))
                        .then(argument("text", StringArgumentType.greedyString()).executes(this::executeNote))))
                .then(literal("assign").then(argument("id", IntegerArgumentType.integer(1)).executes(this::executeAssign)))
                .then(literal("close").then(argument("id", IntegerArgumentType.integer(1)).executes(this::executeClose)));
    }

    private int executeOpen(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            Msg.send(sender, lang.get("common.player-not-found"));
            return 0;
        }
        String subject = StringArgumentType.getString(ctx, "subject");
        Ticket ticket = service.open(player.getUniqueId(), subject, subject, null);
        Msg.send(sender, lang.get("ticket.opened", ticket.getId(), ticket.getSubject()));
        return Command.SINGLE_SUCCESS;
    }

    private int executeList(CommandContext<CommandSourceStack> ctx, TicketStatus status) {
        CommandSender sender = ctx.getSource().getSender();
        printList(sender, service.list(status));
        return Command.SINGLE_SUCCESS;
    }

    private int executeMine(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            Msg.send(sender, lang.get("ticket.list-empty"));
            return 0;
        }
        printList(sender, service.assignedTo(player.getUniqueId()));
        return Command.SINGLE_SUCCESS;
    }

    private void printList(CommandSender sender, List<Ticket> ticketList) {
        if (ticketList.isEmpty()) {
            Msg.send(sender, lang.get("ticket.list-empty"));
            return;
        }
        Msg.send(sender, lang.get("ticket.list-header"));
        for (Ticket t : ticketList) {
            Msg.send(sender, lang.get("ticket.list-line", t.getId(), t.getStatus(), t.getPriority(), t.getSubject(), t.getOpener()));
        }
    }

    private int executeView(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        long id = IntegerArgumentType.getInteger(ctx, "id");
        Optional<Ticket> found = service.find(id);
        if (found.isEmpty()) {
            Msg.send(sender, lang.get("ticket.not-found", id));
            return 0;
        }
        Ticket t = found.get();
        Msg.send(sender, lang.get("ticket.view-header", t.getId(), t.getSubject()));
        Msg.send(sender, lang.get("ticket.view-status", t.getStatus(), t.getPriority(),
                t.getAssignee() == null ? lang.get("ticket.view-none") : t.getAssignee()));
        for (TicketMessage m : service.messages(id)) {
            String internalTag = m.isInternal() ? lang.get("ticket.view-internal-tag") : "";
            Msg.send(sender, lang.get("ticket.view-message", m.getSentAt(), m.getAuthor(), internalTag, m.getBody()));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int executeReply(CommandContext<CommandSourceStack> ctx) {
        return doReply(ctx, false);
    }

    private int executeNote(CommandContext<CommandSourceStack> ctx) {
        return doReply(ctx, true);
    }

    private int doReply(CommandContext<CommandSourceStack> ctx, boolean internal) {
        CommandSender sender = ctx.getSource().getSender();
        long id = IntegerArgumentType.getInteger(ctx, "id");
        String text = StringArgumentType.getString(ctx, "text");
        UUID staffUuid = sender instanceof Player p ? p.getUniqueId() : null;
        if (service.reply(id, staffUuid, text, internal).isEmpty()) {
            Msg.send(sender, lang.get("ticket.not-found", id));
            return 0;
        }
        Msg.send(sender, lang.get("ticket.replied", id));
        return Command.SINGLE_SUCCESS;
    }

    private int executeAssign(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        long id = IntegerArgumentType.getInteger(ctx, "id");
        UUID staffUuid = sender instanceof Player p ? p.getUniqueId() : null;
        if (service.assign(id, staffUuid).isEmpty()) {
            Msg.send(sender, lang.get("ticket.not-found", id));
            return 0;
        }
        Msg.send(sender, lang.get("ticket.assigned", id));
        return Command.SINGLE_SUCCESS;
    }

    private int executeClose(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        long id = IntegerArgumentType.getInteger(ctx, "id");
        UUID staffUuid = sender instanceof Player p ? p.getUniqueId() : null;
        if (service.close(id, staffUuid).isEmpty()) {
            Msg.send(sender, lang.get("ticket.not-found", id));
            return 0;
        }
        Msg.send(sender, lang.get("ticket.closed", id));
        return Command.SINGLE_SUCCESS;
    }
}
