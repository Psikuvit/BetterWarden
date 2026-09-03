package me.psikuvit.betterWarden.command;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

/** CommandSender#sendMessage(String) does not parse MiniMessage tags - always go through this instead. */
public final class Msg {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private Msg() {
    }

    public static void send(CommandSender sender, String miniMessageText) {
        sender.sendMessage(MINI_MESSAGE.deserialize(miniMessageText));
    }
}
