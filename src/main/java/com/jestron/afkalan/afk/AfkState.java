package com.jestron.afkalan.afk;

import com.jestron.afkalan.display.AfkBoard;
import com.jestron.afkalan.zone.AfkZone;

/** Alan içindeki bir oyuncunun durumu. PlaceholderAPI async okuyabileceği için alanlar volatile. */
public final class AfkState {

    volatile AfkZone zone;
    volatile int remaining;
    volatile double nextReward;
    AfkBoard board;

    AfkState(AfkZone zone) {
        this.zone = zone;
        this.remaining = zone.getCooldown();
    }

    public AfkZone zone() {
        return zone;
    }

    public int remaining() {
        return remaining;
    }

    /** Oyuncunun bu alandaki ödül miktarı (vip yetkisi dahil). */
    public double nextReward() {
        return nextReward;
    }
}
