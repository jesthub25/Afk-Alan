package com.jestron.afkalan.market;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * Market envanterinin sahibi. Envanter tanıma başlıkla değil bu sınıfla yapılır
 * (başlık karşılaştırması sürüm/renk farklarında bozulur, InventoryView 1.21'de değişti).
 */
public final class MarketMenu implements InventoryHolder {

    private final UUID viewer;
    private final Inventory inventory;
    int version = -1;
    boolean framed;

    MarketMenu(UUID viewer, String title) {
        this.viewer = viewer;
        this.inventory = Bukkit.createInventory(this, MarketManager.SIZE, title);
    }

    public UUID getViewer() {
        return viewer;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
