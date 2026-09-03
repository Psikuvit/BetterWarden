package me.psikuvit.betterWarden.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/** Not Folia-aware yet — routes through here so that swap is one class, not every call site. */
public class PaperScheduler implements WardenScheduler {

    private final Plugin plugin;

    public PaperScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void runGlobal(Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }
}
