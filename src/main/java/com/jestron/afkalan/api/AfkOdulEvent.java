package com.jestron.afkalan.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Oyuncu bir AFK alanında ödül almadan hemen önce çağrılır (ana thread).
 * Diğer eklentiler miktarı değiştirebilir veya ödülü iptal edebilir.
 */
public final class AfkOdulEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String zone;
    private double amount;
    private boolean cancelled;

    public AfkOdulEvent(Player player, String zone, double amount) {
        this.player = player;
        this.zone = zone;
        this.amount = amount;
    }

    public Player getPlayer() {
        return player;
    }

    public String getZone() {
        return zone;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = Math.max(0D, amount);
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
