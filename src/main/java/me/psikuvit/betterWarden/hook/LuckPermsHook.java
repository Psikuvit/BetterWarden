package me.psikuvit.betterWarden.hook;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * Only ever constructed after confirming the LuckPerms plugin is present (see
 * BetterWarden.onEnable) - referencing LuckPerms types is safe here precisely because the JVM
 * doesn't link this class until something actually calls {@code new LuckPermsHook()}.
 */
public class LuckPermsHook {

    private final LuckPerms luckPerms;

    public LuckPermsHook() {
        this.luckPerms = LuckPermsProvider.get();
    }

    public Optional<String> primaryGroup(Player player) {
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        return user == null ? Optional.empty() : Optional.ofNullable(user.getCachedData().getMetaData().getPrimaryGroup());
    }
}
