package com.jestron.afkalan.market;

import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Market envanteri koruması. Sadece event.getInventory() (üst envanter) kullanılır;
 * InventoryView'a hiç dokunulmaz (1.21'de sınıf -> arayüz değişimi ikili uyumluluğu bozuyordu).
 */
public final class MarketListener implements Listener {

    private final MarketManager market;

    public MarketListener(MarketManager market) {
        this.market = market;
    }

    private static MarketMenu menuOf(Inventory top) {
        if (top == null) return null;
        InventoryHolder h = top.getHolder();
        return h instanceof MarketMenu ? (MarketMenu) h : null;
    }

    // En düşük öncelik: diğer eklentilerden önce iptal ederiz; ignoreCancelled yok ki hiçbir tıklama kaçmasın.
    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent e) {
        MarketMenu menu = menuOf(e.getInventory());
        if (menu == null) return;
        // Market açıkken her türlü tıklama iptal: shift-tık, sayı tuşu, çift tık, alt envanter dahil.
        e.setCancelled(true);

        HumanEntity who = e.getWhoClicked();
        if (!(who instanceof Player)) return;
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getInventory()) return;
        ClickType type = e.getClick();
        if (type == ClickType.DOUBLE_CLICK || type == ClickType.UNKNOWN) return;
        int slot = MarketManager.slotOf(e.getRawSlot());
        if (slot < 1) return;
        market.purchase((Player) who, menu, slot);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent e) {
        if (menuOf(e.getInventory()) != null) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent e) {
        MarketMenu menu = menuOf(e.getInventory());
        if (menu != null) market.onClose(menu);
    }
}
