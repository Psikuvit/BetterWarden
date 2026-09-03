package me.psikuvit.betterWarden.gui;

import me.psikuvit.betterWarden.core.model.Ticket;
import me.psikuvit.betterWarden.core.model.TicketMessage;
import me.psikuvit.betterWarden.core.service.ChatInputService;
import me.psikuvit.betterWarden.core.service.TicketService;
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

public class TicketDetailMenu extends WardenMenu {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    public TicketDetailMenu(Long ticketId, TicketService service, ChatInputService chatInput) {
        super(27, "<dark_gray>Ticket #" + ticketId);

        Optional<Ticket> found = service.find(ticketId);
        if (found.isEmpty()) {
            ItemStack missing = new ItemStack(Material.BARRIER);
            ItemMeta meta = missing.getItemMeta();
            meta.displayName(MM.deserialize("<red>Ticket not found"));
            missing.setItemMeta(meta);
            setItem(13, missing);
            return;
        }
        Ticket t = found.get();

        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.displayName(MM.deserialize("<white>" + t.getSubject()));
        List<Component> lore = new ArrayList<>();
        lore.add(MM.deserialize("<gray>Status: " + t.getStatus() + " | Priority: " + t.getPriority()));
        lore.add(MM.deserialize("<gray>Opener: " + t.getOpener()));
        lore.add(MM.deserialize("<gray>Assignee: " + (t.getAssignee() == null ? "none" : t.getAssignee())));
        for (TicketMessage m : lastMessages(service, ticketId, 5)) {
            String tag = m.isInternal() ? " (internal)" : "";
            lore.add(MM.deserialize("<dark_gray>" + m.getAuthor() + tag + ": " + truncate(m.getBody(), 40)));
        }
        infoMeta.lore(lore);
        info.setItemMeta(infoMeta);
        setItem(13, info);

        setItem(10, actionItem(Material.LIME_WOOL, "<green>Assign to me"), e -> {
            if (e.getWhoClicked() instanceof Player staff) {
                service.assign(ticketId, staff.getUniqueId());
                staff.sendMessage(MM.deserialize("<green>Ticket #" + ticketId + " assigned to you."));
                staff.closeInventory();
            }
        });

        setItem(12, actionItem(Material.WRITABLE_BOOK, "<yellow>Reply"), e -> {
            if (!(e.getWhoClicked() instanceof Player staff)) {
                return;
            }
            staff.closeInventory();
            staff.sendMessage(MM.deserialize("<yellow>Type your reply in chat, or 'cancel' to abort."));
            chatInput.awaitInput(staff.getUniqueId(), text -> {
                if ("cancel".equalsIgnoreCase(text.trim())) {
                    return;
                }
                service.reply(ticketId, staff.getUniqueId(), text, false);
                staff.sendMessage(MM.deserialize("<green>Reply added to ticket #" + ticketId + "."));
                notifyOpener(t, text);
            });
        });

        setItem(14, actionItem(Material.BOOK, "<gold>Internal Note"), e -> {
            if (!(e.getWhoClicked() instanceof Player staff)) {
                return;
            }
            staff.closeInventory();
            staff.sendMessage(MM.deserialize("<yellow>Type the staff-only note in chat, or 'cancel' to abort."));
            chatInput.awaitInput(staff.getUniqueId(), text -> {
                if ("cancel".equalsIgnoreCase(text.trim())) {
                    return;
                }
                service.reply(ticketId, staff.getUniqueId(), text, true);
                staff.sendMessage(MM.deserialize("<green>Internal note added to ticket #" + ticketId + "."));
            });
        });

        setItem(16, actionItem(Material.RED_WOOL, "<red>Close"), e -> {
            if (e.getWhoClicked() instanceof Player staff) {
                service.close(ticketId, staff.getUniqueId());
                staff.sendMessage(MM.deserialize("<green>Ticket #" + ticketId + " closed."));
                staff.closeInventory();
            }
        });

        setItem(22, actionItem(Material.BARRIER, "<gray>Close Menu"), e -> e.getWhoClicked().closeInventory());
    }

    private void notifyOpener(Ticket ticket, String replyText) {
        try {
            Player opener = Bukkit.getPlayer(UUID.fromString(ticket.getOpener()));
            if (opener != null) {
                opener.sendMessage(MM.deserialize("<gold>[Ticket #" + ticket.getId() + "] <white>" + replyText));
            }
        } catch (IllegalArgumentException ignored) {
            // opener stored as something other than a UUID string - nothing to notify.
        }
    }

    private static List<TicketMessage> lastMessages(TicketService service, Long ticketId, int limit) {
        List<TicketMessage> all = service.messages(ticketId);
        return all.size() <= limit ? all : all.subList(all.size() - limit, all.size());
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }

    private ItemStack actionItem(Material material, String nameMiniMessage) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(nameMiniMessage));
        item.setItemMeta(meta);
        return item;
    }
}
