package com.jestron.afkalan;

import com.jestron.afkalan.util.Text;
import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * config.yml değerleri yüklenirken bir kez okunur ve renklendirilir.
 * Görevler her saniye config okumaz; sadece bu nesnenin alanlarına erişir.
 */
public final class Settings {

    public enum CounterMode { TITLE, ACTIONBAR, IKISI, KAPALI }

    public static final int MAX_LINES = 15;
    public static final int MAX_LINE_LENGTH = 64;

    private final AfkAlan plugin;
    private final Map<String, String> messages = new HashMap<>();

    public String currency;
    public String noneText, yesText, noText;

    public int defCooldown;
    public double defReward, defVipReward;
    public String defVipPerm;

    public Set<GameMode> ignoredModes = EnumSet.noneOf(GameMode.class);

    public CounterMode counterMode;
    public String title, subtitle, actionbar;
    public int titleStay;
    public String colorHigh, colorMid, colorLow;
    public int barLength;
    public String barFull, barEmpty;
    public boolean titleNeedsBar, subtitleNeedsBar, actionbarNeedsBar;

    public String soundReward, soundBuy, soundFail;

    public boolean boardEnabled, boardHideNumbers;
    public String boardTitle;
    public String[] boardLines = new String[0];
    public boolean[] boardLinePapi = new boolean[0];
    public boolean[] boardLineStatic = new boolean[0];

    public String marketTitle;
    public List<String> marketLore = Collections.emptyList();
    public String marketOk, marketNo, marketAdmin;
    public long buyCooldownMs;

    public int autosaveMinutes;

    Settings(AfkAlan plugin) {
        this.plugin = plugin;
    }

    void load(boolean papi) {
        FileConfiguration c = plugin.getConfig();

        currency = Text.color(c.getString("para-birimi", "AFK Parası"));
        noneText = Text.color(c.getString("yok-metni", "Yok"));
        yesText = Text.color(c.getString("evet-metni", "Evet"));
        noText = Text.color(c.getString("hayir-metni", "Hayır"));

        defCooldown = Math.max(1, c.getInt("varsayilan-alan.cooldown", 60));
        defReward = Math.max(0D, c.getDouble("varsayilan-alan.odul", 1D));
        defVipReward = Math.max(0D, c.getDouble("varsayilan-alan.vip-odul", 2D));
        defVipPerm = c.getString("varsayilan-alan.vip-yetki", "afk.vipafk");

        EnumSet<GameMode> modes = EnumSet.noneOf(GameMode.class);
        for (String s : c.getStringList("sayilmayan-oyun-modlari")) {
            try {
                modes.add(GameMode.valueOf(s.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("config.yml: geçersiz oyun modu '" + s + "'");
            }
        }
        ignoredModes = modes;

        try {
            counterMode = CounterMode.valueOf(c.getString("sayac.gosterim", "TITLE").trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("config.yml: sayac.gosterim geçersiz, TITLE kullanılıyor.");
            counterMode = CounterMode.TITLE;
        }
        title = Text.color(c.getString("sayac.title", "{renk}&l{kalan}"));
        subtitle = Text.color(c.getString("sayac.subtitle", "&7{alan}"));
        actionbar = Text.color(c.getString("sayac.actionbar", "&7{alan} &8» {renk}{kalan}s"));
        titleStay = Math.max(21, c.getInt("sayac.title-sure-tick", 30));
        colorHigh = Text.color(c.getString("sayac.renk-yuksek", "&a"));
        colorMid = Text.color(c.getString("sayac.renk-orta", "&e"));
        colorLow = Text.color(c.getString("sayac.renk-dusuk", "&c"));
        barLength = Math.max(1, Math.min(60, c.getInt("sayac.bar-uzunluk", 20)));
        barFull = Text.color(c.getString("sayac.bar-dolu", "&a|"));
        barEmpty = Text.color(c.getString("sayac.bar-bos", "&7|"));
        titleNeedsBar = title.contains("{bar}");
        subtitleNeedsBar = subtitle.contains("{bar}");
        actionbarNeedsBar = actionbar.contains("{bar}");

        soundReward = c.getString("sesler.odul", "");
        soundBuy = c.getString("sesler.satin-alma", "");
        soundFail = c.getString("sesler.basarisiz", "");

        boardEnabled = c.getBoolean("scoreboard.aktif", true);
        boardHideNumbers = c.getBoolean("scoreboard.sayilari-gizle", true);
        boardTitle = Text.limit(Text.color(c.getString("scoreboard.baslik", "&6&lAFK PANEL")), 128);
        List<String> raw = c.getStringList("scoreboard.satirlar");
        if (raw.size() > MAX_LINES) {
            plugin.getLogger().warning("config.yml: scoreboard en fazla " + MAX_LINES + " satır olabilir, fazlası yok sayıldı.");
            raw = raw.subList(0, MAX_LINES);
        }
        boardLines = new String[raw.size()];
        boardLinePapi = new boolean[raw.size()];
        boardLineStatic = new boolean[raw.size()];
        for (int i = 0; i < raw.size(); i++) {
            String line = Text.color(raw.get(i) == null ? "" : raw.get(i));
            boardLines[i] = line;
            boardLinePapi[i] = papi && line.indexOf('%') >= 0;
            boardLineStatic[i] = !boardLinePapi[i] && line.indexOf('{') < 0;
        }
        if (boardLines.length == 0) boardEnabled = false;

        marketTitle = Text.color(c.getString("market.baslik", "&8&lAFK Market"));
        List<String> lore = new ArrayList<>();
        for (String s : c.getStringList("market.lore")) lore.add(Text.color(s));
        marketLore = Collections.unmodifiableList(lore);
        marketOk = Text.color(c.getString("market.durum-yeterli", "&a» Satın almak için tıkla"));
        marketNo = Text.color(c.getString("market.durum-yetersiz", "&c» Yetersiz bakiye"));
        marketAdmin = Text.color(c.getString("market.admin-lore", ""));
        buyCooldownMs = Math.max(0L, c.getLong("market.satin-alma-bekleme-ms", 250L));

        autosaveMinutes = Math.max(1, c.getInt("otomatik-kayit-dakika", 5));

        loadMessages(c);
    }

    private void loadMessages(FileConfiguration c) {
        messages.clear();
        Set<String> keys = new LinkedHashSet<>();
        Configuration defs = c.getDefaults();
        if (defs != null) {
            ConfigurationSection d = defs.getConfigurationSection("mesajlar");
            if (d != null) keys.addAll(d.getKeys(false));
        }
        ConfigurationSection own = c.getConfigurationSection("mesajlar");
        if (own != null) keys.addAll(own.getKeys(false));

        String prefix = Text.color(c.getString("mesajlar.prefix", ""));
        for (String key : keys) {
            if (key.equals("prefix")) continue;
            String raw = c.getString("mesajlar." + key, "");
            // {prefix} ve {para} sabittir, yüklemede bir kez değiştirilir.
            messages.put(key, Text.color(raw).replace("{prefix}", prefix).replace("{para}", currency));
        }
    }

    public String msg(String key) {
        String m = messages.get(key);
        return m == null ? "" : m;
    }

    /** Mesajı gönderir. Mesaj config'de boş bırakılmışsa hiçbir şey gönderilmez. */
    public void send(CommandSender to, String key, String... kv) {
        if (to == null) return;
        String m = msg(key);
        if (m.isEmpty()) return;
        to.sendMessage(Text.replace(m, kv));
    }

    public void usage(CommandSender to, String usage) {
        send(to, "kullanim", "{kullanim}", usage);
    }
}
