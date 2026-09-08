package me.psikuvit.betterWarden.spigot.command;

import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

/** No native Adventure on Spigot - CommandSender#sendMessage(String) doesn't parse MiniMessage, always go through this instead. */
public final class SpigotMsg {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final BukkitAudiences audiences;

    public SpigotMsg(BukkitAudiences audiences) {
        this.audiences = audiences;
    }

    public void send(CommandSender sender, String miniMessageText) {
        audiences.sender(sender).sendMessage(MINI_MESSAGE.deserialize(miniMessageText));
    }
}
