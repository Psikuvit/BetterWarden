package me.psikuvit.betterWarden.command;

import org.bukkit.Bukkit;

/**
 * Runs a command's post-target-resolution continuation safely: inline if still on the main
 * thread (the common case - online players resolve synchronously), otherwise hopped back via
 * the legacy BukkitScheduler for the rare case a Mojang lookup was needed. Same reasoning as
 * warden-paper's own Async helper; no Folia region-scheduler concern on plain Spigot.
 */
public final class Async {

    private Async() {
    }

    public static void runOnMain(org.bukkit.plugin.Plugin plugin, Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }
}
