package me.psikuvit.betterWarden.gui;

import me.psikuvit.betterWarden.core.model.Report;
import me.psikuvit.betterWarden.core.model.ReportStatus;
import me.psikuvit.betterWarden.core.service.PlayerTrackingService;
import me.psikuvit.betterWarden.core.service.ReportService;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.UUID;

public class ReportQueueMenu extends WardenMenu {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    public ReportQueueMenu(ReportService service, PlayerTrackingService playerTracking, ReportStatus filter) {
        super(54, "<dark_gray>Reports" + (filter == null ? "" : ": " + filter));

        List<Report> reports = service.list(filter);
        for (int i = 0; i < 45; i++) {
            if (i >= reports.size()) {
                clearItem(i);
                continue;
            }
            Report r = reports.get(i);
            setItem(i, reportItem(r, playerTracking), e -> {
                if (e.getWhoClicked() instanceof Player staff) {
                    new ReportDetailMenu(r.getId(), service, playerTracking).open(staff);
                }
            });
        }

        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        closeMeta.displayName(MM.deserialize("<gray>Close"));
        close.setItemMeta(closeMeta);
        setItem(49, close, e -> e.getWhoClicked().closeInventory());
    }

    private ItemStack reportItem(Report r, PlayerTrackingService playerTracking) {
        String targetName = playerTracking.find(UUID.fromString(r.getTarget())).map(me.psikuvit.betterWarden.core.model.Player::getLastName).orElse(r.getTarget());
        Material material = switch (r.getStatus()) {
            case OPEN -> Material.YELLOW_WOOL;
            case CLAIMED -> Material.LIME_WOOL;
            case DISMISSED, CLOSED -> Material.GRAY_WOOL;
        };
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize("<white>#" + r.getId() + " " + targetName));
        meta.lore(List.of(
                MM.deserialize("<gray>Status: " + r.getStatus()),
                MM.deserialize("<gray>" + r.getReason())
        ));
        item.setItemMeta(meta);
        return item;
    }
}
