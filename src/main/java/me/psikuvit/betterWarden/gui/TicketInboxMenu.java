package me.psikuvit.betterWarden.gui;

import me.psikuvit.betterWarden.core.model.Ticket;
import me.psikuvit.betterWarden.core.model.TicketStatus;
import me.psikuvit.betterWarden.core.service.ChatInputService;
import me.psikuvit.betterWarden.core.service.TicketService;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class TicketInboxMenu extends WardenMenu {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    public TicketInboxMenu(TicketService service, ChatInputService chatInput, TicketStatus filter) {
        super(54, "<dark_gray>Tickets" + (filter == null ? "" : ": " + filter));

        List<Ticket> tickets = service.list(filter);
        for (int i = 0; i < 45; i++) {
            if (i >= tickets.size()) {
                clearItem(i);
                continue;
            }
            Ticket t = tickets.get(i);
            setItem(i, ticketItem(t), e -> {
                if (e.getWhoClicked() instanceof Player staff) {
                    new TicketDetailMenu(t.getId(), service, chatInput).open(staff);
                }
            });
        }

        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        closeMeta.displayName(MM.deserialize("<gray>Close"));
        close.setItemMeta(closeMeta);
        setItem(49, close, e -> e.getWhoClicked().closeInventory());
    }

    private ItemStack ticketItem(Ticket t) {
        Material material = switch (t.getStatus()) {
            case OPEN -> Material.YELLOW_WOOL;
            case IN_PROGRESS -> Material.LIME_WOOL;
            case CLOSED -> Material.GRAY_WOOL;
        };
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize("<white>#" + t.getId() + " " + t.getSubject()));
        meta.lore(List.of(
                MM.deserialize("<gray>Status: " + t.getStatus() + " | Priority: " + t.getPriority()),
                MM.deserialize("<gray>Opened by " + t.getOpener())
        ));
        item.setItemMeta(meta);
        return item;
    }
}
