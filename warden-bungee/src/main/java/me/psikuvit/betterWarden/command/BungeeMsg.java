package me.psikuvit.betterWarden.command;

import net.kyori.adventure.platform.bungeecord.BungeeAudiences;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.md_5.bungee.api.CommandSender;

/** BungeeCord has no native Adventure support - CommandSender#sendMessage(String) doesn't parse MiniMessage, so always go through this instead. */
public final class BungeeMsg {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final BungeeAudiences audiences;

    public BungeeMsg(BungeeAudiences audiences) {
        this.audiences = audiences;
    }

    public void send(CommandSender sender, String miniMessageText) {
        audiences.sender(sender).sendMessage(MINI_MESSAGE.deserialize(miniMessageText));
    }
}
