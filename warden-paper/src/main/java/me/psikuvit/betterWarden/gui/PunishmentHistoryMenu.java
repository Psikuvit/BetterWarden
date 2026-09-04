package me.psikuvit.betterWarden.gui;

import me.psikuvit.betterWarden.core.model.Punishment;
import me.psikuvit.betterWarden.core.service.PunishmentService;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.UUID;

/** Paginated, right-click to revoke (docs/spec/02-PLUGIN-PAPER.txt §4). */
public class PunishmentHistoryMenu extends WardenMenu {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int PAGE_SIZE = 45;

    private final PunishmentService service;
    private final UUID targetUuid;
    private List<Punishment> all;
    private int page = 0;

    public PunishmentHistoryMenu(UUID targetUuid, String targetName, PunishmentService service) {
        super(54, "<dark_gray>History: <white>" + targetName);
        this.targetUuid = targetUuid;
        this.service = service;
        this.all = service.history(targetUuid, 200);
        render();
    }

    private void render() {
        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE; i++) {
            int index = start + i;
            if (index >= all.size()) {
                clearItem(i);
                continue;
            }
            Punishment p = all.get(index);
            setItem(i, punishmentItem(p), e -> {
                if (p.isActive() && e.getClick().isRightClick() && e.getWhoClicked() instanceof Player staff) {
                    service.revoke(p.getId(), staff.getUniqueId(), "Revoked via GUI");
                    refresh();
                }
            });
        }
        renderControls();
    }

    private void refresh() {
        this.all = service.history(targetUuid, 200);
        render();
    }

    private void renderControls() {
        if (page > 0) {
            setItem(45, controlItem(Material.ARROW, "<white>Previous Page"), e -> {
                page--;
                render();
            });
        } else {
            clearItem(45);
        }
        setItem(49, controlItem(Material.BARRIER, "<gray>Close"), e -> e.getWhoClicked().closeInventory());
        if ((long) (page + 1) * PAGE_SIZE < all.size()) {
            setItem(53, controlItem(Material.ARROW, "<white>Next Page"), e -> {
                page++;
                render();
            });
        } else {
            clearItem(53);
        }
    }

    private ItemStack punishmentItem(Punishment p) {
        Material material = p.isActive() ? Material.RED_WOOL : Material.GRAY_WOOL;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize((p.isActive() ? "<red>" : "<gray>") + "#" + p.getId() + " " + p.getType()));
        meta.lore(List.of(
                MM.deserialize("<gray>Reason: " + p.getReason()),
                MM.deserialize("<gray>Status: " + (p.isActive() ? "ACTIVE" : "expired/revoked")),
                MM.deserialize(p.isActive() ? "<yellow>Right-click to revoke" : "<dark_gray>(inactive)")
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack controlItem(Material material, String nameMiniMessage) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(nameMiniMessage));
        item.setItemMeta(meta);
        return item;
    }
}
