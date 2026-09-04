package me.psikuvit.betterWarden.gui;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Minimal reusable menu base: named slots, per-slot click handlers, nothing else yet (no pagination/anvil-input framework). */
public abstract class WardenMenu implements InventoryHolder {

    private final Inventory inventory;
    private final Map<Integer, Consumer<InventoryClickEvent>> handlers = new HashMap<>();

    protected WardenMenu(int size, String titleMiniMessage) {
        this.inventory = Bukkit.createInventory(this, size, MiniMessage.miniMessage().deserialize(titleMiniMessage));
    }

    @Override
    public @NonNull Inventory getInventory() {
        return inventory;
    }

    protected void setItem(int slot, ItemStack item) {
        setItem(slot, item, null);
    }

    protected void setItem(int slot, ItemStack item, Consumer<InventoryClickEvent> onClick) {
        inventory.setItem(slot, item);
        if (onClick != null) {
            handlers.put(slot, onClick);
        } else {
            // Re-rendering a paginated menu must drop the old slot's handler too, not just its
            // item - otherwise a stale handler from a previous page can fire on an now-empty slot.
            handlers.remove(slot);
        }
    }

    protected void clearItem(int slot) {
        setItem(slot, null, null);
    }

    void handleClick(InventoryClickEvent event) {
        Consumer<InventoryClickEvent> handler = handlers.get(event.getRawSlot());
        if (handler != null) {
            handler.accept(event);
        }
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }
}
