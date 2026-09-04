package me.psikuvit.betterWarden.command;

import me.psikuvit.betterWarden.scheduler.WardenScheduler;
import org.bukkit.Bukkit;

/**
 * Runs a command's post-target-resolution continuation safely: inline if still on the main
 * thread (the common case - online/cached players resolve synchronously, so the continuation
 * never actually leaves the thread that called .executes()), otherwise hopped back via the
 * scheduler for the rare case a Mojang lookup was needed. Bukkit API - sendMessage included -
 * is not guaranteed safe off the main thread, so every continuation must go through this.
 */
public final class Async {

    private Async() {
    }

    public static void runOnMain(WardenScheduler scheduler, Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            scheduler.runGlobal(task);
        }
    }
}
