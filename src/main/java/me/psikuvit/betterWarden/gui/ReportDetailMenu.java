package me.psikuvit.betterWarden.gui;

import me.psikuvit.betterWarden.core.model.Report;
import me.psikuvit.betterWarden.core.service.PlayerTrackingService;
import me.psikuvit.betterWarden.core.service.ReportService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ReportDetailMenu extends WardenMenu {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    public ReportDetailMenu(Long reportId, ReportService service, PlayerTrackingService playerTracking) {
        super(27, "<dark_gray>Report #" + reportId);

        Optional<Report> found = service.find(reportId);
        if (found.isEmpty()) {
            ItemStack missing = new ItemStack(Material.BARRIER);
            ItemMeta meta = missing.getItemMeta();
            meta.displayName(MM.deserialize("<red>Report not found"));
            missing.setItemMeta(meta);
            setItem(13, missing);
            return;
        }
        Report r = found.get();
        String targetName = playerTracking.find(UUID.fromString(r.getTarget())).map(me.psikuvit.betterWarden.core.model.Player::getLastName).orElse(r.getTarget());

        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.displayName(MM.deserialize("<white>Report #" + r.getId()));
        List<Component> lore = new ArrayList<>();
        lore.add(MM.deserialize("<gray>Target: <white>" + targetName));
        lore.add(MM.deserialize("<gray>Reporter: " + r.getReporter()));
        lore.add(MM.deserialize("<gray>Reason: " + r.getReason()));
        lore.add(MM.deserialize("<gray>Status: " + r.getStatus()));
        if (r.getLocation() != null) {
            lore.add(MM.deserialize("<gray>Location: " + r.getLocation()));
        }
        infoMeta.lore(lore);
        info.setItemMeta(infoMeta);
        setItem(13, info);

        setItem(10, actionItem(Material.LIME_WOOL, "<green>Claim"), e -> {
            if (e.getWhoClicked() instanceof Player staff) {
                service.claim(reportId, staff.getUniqueId());
                staff.sendMessage(MM.deserialize("<green>Report #" + reportId + " claimed."));
                staff.closeInventory();
            }
        });

        setItem(12, actionItem(Material.ENDER_PEARL, "<aqua>Teleport"), e -> {
            if (!(e.getWhoClicked() instanceof Player staff)) {
                return;
            }
            Player target = Bukkit.getPlayer(UUID.fromString(r.getTarget()));
            if (target == null) {
                staff.sendMessage(MM.deserialize("<red>" + targetName + " is not online."));
                return;
            }
            staff.closeInventory();
            staff.teleport(target.getLocation());
        });

        setItem(14, actionItem(Material.WRITTEN_BOOK, "<yellow>View Chat"), e -> {
            if (!(e.getWhoClicked() instanceof Player staff)) {
                return;
            }
            staff.closeInventory();
            String snapshot = r.getChatSnapshot();
            if (snapshot == null || snapshot.isBlank()) {
                staff.sendMessage(MM.deserialize("<gray>(no recent chat captured)"));
                return;
            }
            for (String line : snapshot.split("\n")) {
                staff.sendMessage(line);
            }
        });

        setItem(16, actionItem(Material.RED_WOOL, "<red>Dismiss"), e -> {
            if (e.getWhoClicked() instanceof Player staff) {
                service.dismiss(reportId, staff.getUniqueId());
                staff.sendMessage(MM.deserialize("<green>Report #" + reportId + " dismissed."));
                staff.closeInventory();
            }
        });

        setItem(22, actionItem(Material.BARRIER, "<gray>Close"), e -> e.getWhoClicked().closeInventory());
    }

    private ItemStack actionItem(Material material, String nameMiniMessage) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(nameMiniMessage));
        item.setItemMeta(meta);
        return item;
    }
}
