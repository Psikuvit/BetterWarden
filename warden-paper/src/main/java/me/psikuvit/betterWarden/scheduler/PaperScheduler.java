package me.psikuvit.betterWarden.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Folia-safe: Bukkit.getScheduler().runTask() throws UnsupportedOperationException on Folia
 * (the legacy global scheduler is disabled there). getGlobalRegionScheduler() is the unified
 * replacement Paper ships on every build, Folia or not - on plain Paper it just runs on the
 * main thread like the old call did, on Folia it runs on the global region thread. Confirmed
 * both methods exist on this exact pinned paper-api version via javap before relying on it.
 */
public class PaperScheduler implements WardenScheduler {

    private final Plugin plugin;

    public PaperScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void runGlobal(Runnable task) {
        Bukkit.getGlobalRegionScheduler().execute(plugin, task);
    }
}
