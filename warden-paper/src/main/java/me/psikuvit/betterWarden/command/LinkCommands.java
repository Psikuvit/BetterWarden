package me.psikuvit.betterWarden.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.psikuvit.betterWarden.core.discord.DiscordLinkService;
import me.psikuvit.betterWarden.core.service.LangService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;

/**
 * docs/spec/05-DISCORD-BOT.txt §6 - the in-game half of account linking. `/link` in Discord
 * mints the code; `/link <code>` here redeems it. HOST-mode only, same as ReportCommands and
 * TicketCommands - CLIENT-mode backends have no local DiscordLinkService to redeem against yet
 * (existing gap, not new to this feature - see PLAN.md).
 */
public final class LinkCommands {

    private final DiscordLinkService linkService;
    private final LangService lang;

    private LinkCommands(DiscordLinkService linkService, LangService lang) {
        this.linkService = linkService;
        this.lang = lang;
    }

    public static void register(JavaPlugin plugin, DiscordLinkService linkService, LangService lang) {
        LinkCommands commands = new LinkCommands(linkService, lang);
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands registrar = event.registrar();
            registrar.register(commands.link().build(), "Link your Discord account");
            registrar.register(commands.unlink().build(), "Remove your Discord account link");
        });
    }

    private LiteralArgumentBuilder<CommandSourceStack> link() {
        return literal("link")
                .then(argument("code", StringArgumentType.word())
                        .executes(this::executeLink));
    }

    private LiteralArgumentBuilder<CommandSourceStack> unlink() {
        return literal("unlink").executes(this::executeUnlink);
    }

    private int executeLink(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            Msg.send(sender, lang.get("link.players-only"));
            return 0;
        }
        String code = StringArgumentType.getString(ctx, "code");
        DiscordLinkService.RedeemResult result = linkService.redeem(code, player.getUniqueId());
        switch (result) {
            case DiscordLinkService.RedeemResult.Success s -> Msg.send(sender, lang.get("link.success", s.discordTag()));
            case DiscordLinkService.RedeemResult.InvalidCode ignored -> Msg.send(sender, lang.get("link.invalid-code"));
            case DiscordLinkService.RedeemResult.AlreadyLinkedDiscord ignored -> Msg.send(sender, lang.get("link.discord-already-linked"));
            case DiscordLinkService.RedeemResult.AlreadyLinkedMinecraft ignored -> Msg.send(sender, lang.get("link.minecraft-already-linked"));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int executeUnlink(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            Msg.send(sender, lang.get("link.players-only"));
            return 0;
        }
        boolean removed = linkService.unlinkMinecraft(player.getUniqueId());
        Msg.send(sender, lang.get(removed ? "link.unlinked" : "link.not-linked"));
        return Command.SINGLE_SUCCESS;
    }
}
