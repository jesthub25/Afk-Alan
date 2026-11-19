package com.jestron.afkalan.hook;

import com.jestron.afkalan.AfkAlan;
import com.jestron.afkalan.Settings;
import com.jestron.afkalan.afk.AfkState;
import com.jestron.afkalan.balance.BalanceManager;
import com.jestron.afkalan.util.Num;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.List;
import java.util.Locale;

/**
 * %afk_bakiye%            biçimli bakiye (12.50)
 * %afk_bakiye_ham%        ham sayı (12.5)
 * %afk_para_birimi%       para birimi adı
 * %afk_alan%              bulunduğu AFK alanı (yoksa yok-metni)
 * %afk_alanda%            evet / hayır
 * %afk_kalan%             ödüle kalan saniye (alan dışında 0)
 * %afk_cooldown%          bulunduğu alanın cooldown süresi
 * %afk_odul%              bulunduğu alandaki ödül miktarı
 * %afk_top_<1-100>_isim%  sıralama
 * %afk_top_<1-100>_bakiye%
 *
 * Tüm veriler thread-safe yapılardan okunur (async scoreboard eklentileriyle uyumlu).
 */
public final class PapiExpansion extends PlaceholderExpansion {

    private final AfkAlan plugin;

    public PapiExpansion(AfkAlan plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "afk";
    }

    @Override
    public String getAuthor() {
        return "Jestron";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (params == null) return null;
        String id = params.toLowerCase(Locale.ROOT);
        Settings s = plugin.settings();
        BalanceManager bm = plugin.balances();

        if (id.startsWith("top_")) return top(id, bm, s);
        if (id.equals("para_birimi")) return s.currency;
        if (player == null) return "";

        AfkState st = plugin.tracker().state(player.getUniqueId());
        switch (id) {
            case "bakiye":
                return Num.format(bm.get(player.getUniqueId()));
            case "bakiye_ham":
                return String.valueOf(Num.round(bm.get(player.getUniqueId())));
            case "alan":
                return st == null ? s.noneText : st.zone().getName();
            case "alanda":
                return st == null ? s.noText : s.yesText;
            case "kalan":
                return st == null ? "0" : String.valueOf(st.remaining());
            case "cooldown":
                return st == null ? "0" : String.valueOf(st.zone().getCooldown());
            case "odul":
                return st == null ? "0" : Num.format(st.nextReward());
            default:
                return null;
        }
    }

    private static String top(String id, BalanceManager bm, Settings s) {
        // top_<n>_isim | top_<n>_bakiye
        String[] parts = id.split("_");
        if (parts.length != 3) return null;
        Integer n = Num.parseInt(parts[1]);
        if (n == null || n < 1 || n > 100) return null;
        List<BalanceManager.TopEntry> list = bm.top(n);
        if (list.size() < n) return parts[2].equals("isim") ? "-" : "0";
        BalanceManager.TopEntry e = list.get(n - 1);
        if (parts[2].equals("isim")) return e.name;
        if (parts[2].equals("bakiye")) return Num.format(e.balance);
        return null;
    }
}
