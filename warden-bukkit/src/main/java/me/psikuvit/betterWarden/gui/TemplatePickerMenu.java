package me.psikuvit.betterWarden.gui;

import me.psikuvit.betterWarden.core.model.PunishmentTemplate;
import me.psikuvit.betterWarden.core.model.PunishmentType;
import me.psikuvit.betterWarden.core.service.ChatInputService;
import me.psikuvit.betterWarden.core.service.PunishmentService;
import me.psikuvit.betterWarden.core.service.PunishmentTemplateService;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.UUID;

/**
 * "Click a reason instead of typing" (docs/spec/02-PLUGIN-PAPER.txt §4). Only offers permanent
 * punishments - tempban/tempmute still need a duration and stay command-only for now.
 */
public class TemplatePickerMenu extends WardenMenu {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    public TemplatePickerMenu(PunishmentType type, UUID targetUuid, String targetName,
                               PunishmentTemplateService templates, PunishmentService punishmentService,
                               ChatInputService chatInput) {
        super(27, "<dark_gray>" + type + ": <white>" + targetName);

        List<PunishmentTemplate> matching = templates.list().stream()
                .filter(t -> t.getType() == type)
                .toList();

        int slot = 0;
        for (PunishmentTemplate template : matching) {
            if (slot >= 18) {
                break;
            }
            ItemStack item = new ItemStack(Material.PAPER);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(MM.deserialize("<white>#" + template.getKey()));
            meta.lore(List.of(MM.deserialize("<gray>" + template.getReason())));
            item.setItemMeta(meta);
            setItem(slot, item, e -> {
                if (e.getWhoClicked() instanceof Player staff) {
                    staff.closeInventory();
                    punishmentService.issueFromTemplate(targetUuid, targetName, template, staff.getUniqueId(), false, null);
                }
            });
            slot++;
        }

        ItemStack custom = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta customMeta = custom.getItemMeta();
        customMeta.displayName(MM.deserialize("<yellow>Custom reason..."));
        custom.setItemMeta(customMeta);
        setItem(22, custom, e -> {
            if (!(e.getWhoClicked() instanceof Player staff)) {
                return;
            }
            staff.closeInventory();
            staff.sendMessage(MM.deserialize("<yellow>Type the reason in chat, or 'cancel' to abort."));
            chatInput.awaitInput(staff.getUniqueId(), reason -> {
                if ("cancel".equalsIgnoreCase(reason.trim())) {
                    return;
                }
                punishmentService.issue(targetUuid, targetName, type, reason, staff.getUniqueId(), null, false, null);
            });
        });

        ItemStack cancel = new ItemStack(Material.BARRIER);
        ItemMeta cancelMeta = cancel.getItemMeta();
        cancelMeta.displayName(MM.deserialize("<gray>Cancel"));
        cancel.setItemMeta(cancelMeta);
        setItem(26, cancel, e -> e.getWhoClicked().closeInventory());
    }
}
