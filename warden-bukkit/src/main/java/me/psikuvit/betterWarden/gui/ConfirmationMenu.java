package me.psikuvit.betterWarden.gui;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Generic yes/no gate for anything destructive, per docs/spec/02-PLUGIN-PAPER.txt §4. */
public class ConfirmationMenu extends WardenMenu {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    public ConfirmationMenu(String titleMiniMessage, String description, Runnable onConfirm) {
        super(27, titleMiniMessage);

        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.displayName(MM.deserialize("<white>" + description));
        info.setItemMeta(infoMeta);
        setItem(13, info);

        ItemStack confirm = new ItemStack(Material.LIME_WOOL);
        ItemMeta confirmMeta = confirm.getItemMeta();
        confirmMeta.displayName(MM.deserialize("<green>Confirm"));
        confirm.setItemMeta(confirmMeta);
        setItem(11, confirm, e -> {
            e.getWhoClicked().closeInventory();
            onConfirm.run();
        });

        ItemStack cancel = new ItemStack(Material.RED_WOOL);
        ItemMeta cancelMeta = cancel.getItemMeta();
        cancelMeta.displayName(MM.deserialize("<red>Cancel"));
        cancel.setItemMeta(cancelMeta);
        setItem(15, cancel, e -> e.getWhoClicked().closeInventory());
    }
}
