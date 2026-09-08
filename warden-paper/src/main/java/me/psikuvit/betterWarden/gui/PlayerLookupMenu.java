package me.psikuvit.betterWarden.gui;

import me.psikuvit.betterWarden.core.config.EditionService;
import me.psikuvit.betterWarden.core.model.PunishmentType;
import me.psikuvit.betterWarden.core.service.AltDetectionService;
import me.psikuvit.betterWarden.core.service.ChatInputService;
import me.psikuvit.betterWarden.core.service.PlayerTrackingService;
import me.psikuvit.betterWarden.core.service.PunishmentService;
import me.psikuvit.betterWarden.core.service.PunishmentTemplateService;
import me.psikuvit.betterWarden.core.service.StaffNoteService;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Read-only profile view plus quick actions (Warn/Mute/Ban open a TemplatePickerMenu, Kick is
 * immediate behind a confirmation). Tempban/tempmute need a duration and stay command-only.
 */
public class PlayerLookupMenu extends WardenMenu {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    public PlayerLookupMenu(UUID targetUuid, String targetName, PunishmentService punishmentService,
                             StaffNoteService noteService, AltDetectionService altDetectionService,
                             PlayerTrackingService playerTracking, PunishmentTemplateService templates,
                             ChatInputService chatInput, EditionService edition) {
        super(27, "<dark_gray>Lookup: <white>" + targetName);

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta headMeta = head.getItemMeta();
        headMeta.displayName(MM.deserialize("<white>" + targetName));
        List<String> headLore = new ArrayList<>();
        playerTracking.find(targetUuid).ifPresentOrElse(p -> {
            headLore.add("First seen: " + p.getFirstSeen());
            headLore.add("Last seen: " + p.getLastSeen());
        }, () -> headLore.add("No profile on record."));
        headMeta.lore(headLore.stream().map(l -> MM.deserialize("<gray>" + l)).toList());
        head.setItemMeta(headMeta);
        setItem(4, head);

        setItem(10, infoItem(Material.BOOK, "Active Punishments",
                String.valueOf(punishmentService.activePunishments(targetUuid).size())));

        if (edition.isPaid()) {
            setItem(12, infoItem(Material.WRITABLE_BOOK, "Staff Notes",
                    String.valueOf(noteService.list(targetUuid).size())));

            List<me.psikuvit.betterWarden.core.model.Player> alts = altDetectionService.findAlts(targetUuid);
            String altsValue = alts.isEmpty() ? "none" : alts.stream()
                    .map(me.psikuvit.betterWarden.core.model.Player::getLastName)
                    .limit(5)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("none");
            setItem(14, infoItem(Material.ZOMBIE_HEAD, "Known Alts (" + alts.size() + ")", altsValue));
        } else {
            setItem(12, infoItem(Material.WRITABLE_BOOK, "Staff Notes", "Standard/Network feature"));
            setItem(14, infoItem(Material.ZOMBIE_HEAD, "Known Alts", "Standard/Network feature"));
        }

        setItem(19, actionItem(Material.PAPER, "<yellow>Warn"), e -> {
            if (e.getWhoClicked() instanceof Player staff) {
                new TemplatePickerMenu(PunishmentType.WARN, targetUuid, targetName, templates, punishmentService, chatInput).open(staff);
            }
        });
        setItem(20, actionItem(Material.IRON_CHAIN, "<gold>Mute"), e -> {
            if (e.getWhoClicked() instanceof Player staff) {
                new TemplatePickerMenu(PunishmentType.MUTE, targetUuid, targetName, templates, punishmentService, chatInput).open(staff);
            }
        });
        setItem(24, actionItem(Material.RED_BED, "<red>Kick"), e -> {
            if (!(e.getWhoClicked() instanceof Player staff)) {
                return;
            }
            new ConfirmationMenu("<red>Confirm Kick", "Kick " + targetName + "?", () ->
                    punishmentService.issue(targetUuid, targetName, PunishmentType.KICK, "Kicked via lookup menu",
                            staff.getUniqueId(), null, false, null)
            ).open(staff);
        });
        setItem(25, actionItem(Material.IRON_SWORD, "<dark_red>Ban"), e -> {
            if (e.getWhoClicked() instanceof Player staff) {
                new TemplatePickerMenu(PunishmentType.BAN, targetUuid, targetName, templates, punishmentService, chatInput).open(staff);
            }
        });

        setItem(22, actionItem(Material.BARRIER, "<gray>Close"), e -> e.getWhoClicked().closeInventory());
    }

    private ItemStack infoItem(Material material, String name, String value) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize("<white>" + name));
        meta.lore(List.of(MM.deserialize("<gray>" + value)));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack actionItem(Material material, String nameMiniMessage) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(nameMiniMessage));
        item.setItemMeta(meta);
        return item;
    }
}
