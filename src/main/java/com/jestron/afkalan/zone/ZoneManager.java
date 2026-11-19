package com.jestron.afkalan.zone;

import com.jestron.afkalan.AfkAlan;
import com.jestron.afkalan.Settings;
import com.jestron.afkalan.util.YamlIO;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * alanlar.yml ile birebir senkron AFK alanları.
 * - Konum araması dünya indeksi üzerinden yapılır: oyuncunun dünyasında alan yoksa O(1).
 * - Komutla yapılan her değişiklikten önce dosya diskte elle değiştirilmiş mi kontrol edilir;
 *   değiştirilmişse önce o hali yüklenir, böylece elle yapılan düzenleme ezilmez.
 */
public final class ZoneManager {

    public enum Result { OK, EXISTS, INVALID_NAME, NOT_FOUND, FILE_ERROR }

    private static final Pattern NAME = Pattern.compile("[\\p{L}\\p{N}_-]{1,32}");
    private static final String HEADER =
            "AfkAlan - AFK alanları\n"
                    + "Her alan: dunya, pos1, pos2, cooldown (saniye), odul, vip-odul, vip-yetki\n"
                    + "Düzenledikten sonra oyunda /afk reload yazın.";

    private final AfkAlan plugin;
    private final File file;
    /** küçük harfli ad -> alan (ekleme sırası korunur) */
    private final Map<String, AfkZone> zones = new LinkedHashMap<>();
    /** dünya adı -> o dünyadaki alanlar */
    private volatile Map<String, AfkZone[]> byWorld = Collections.emptyMap();
    private long[] stamp;
    private String lastError;

    public ZoneManager(AfkAlan plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "alanlar.yml");
    }

    /** @return null başarılı, aksi halde hata mesajı (eski veriler korunur) */
    public String load() {
        YamlConfiguration y;
        try {
            y = YamlIO.read(file);
        } catch (Exception e) {
            lastError = YamlIO.error(e);
            plugin.getLogger().severe("alanlar.yml okunamadı, eski alanlar korunuyor: " + lastError);
            return lastError;
        }
        Settings s = plugin.settings();
        Map<String, AfkZone> fresh = new LinkedHashMap<>();
        ConfigurationSection root = y.getConfigurationSection("alanlar");
        if (root != null) {
            for (String name : root.getKeys(false)) {
                ConfigurationSection z = root.getConfigurationSection(name);
                if (z == null) {
                    plugin.getLogger().warning("alanlar.yml: '" + name + "' geçersiz, atlandı.");
                    continue;
                }
                String world = z.getString("dunya", "");
                if (world == null || world.trim().isEmpty()) {
                    plugin.getLogger().warning("alanlar.yml: '" + name + "' için dunya yok, atlandı.");
                    continue;
                }
                if (!z.isConfigurationSection("pos1") || !z.isConfigurationSection("pos2")) {
                    plugin.getLogger().warning("alanlar.yml: '" + name + "' için pos1/pos2 eksik, atlandı.");
                    continue;
                }
                int cd = z.getInt("cooldown", s.defCooldown);
                if (cd < 1) {
                    plugin.getLogger().warning("alanlar.yml: '" + name + "' cooldown en az 1 olmalı, 1 yapıldı.");
                    cd = 1;
                }
                AfkZone zone = new AfkZone(name, world.trim(),
                        z.getInt("pos1.x"), z.getInt("pos1.y"), z.getInt("pos1.z"),
                        z.getInt("pos2.x"), z.getInt("pos2.y"), z.getInt("pos2.z"),
                        cd,
                        z.getDouble("odul", s.defReward),
                        z.getDouble("vip-odul", s.defVipReward),
                        z.getString("vip-yetki", s.defVipPerm));
                String key = name.toLowerCase(Locale.ROOT);
                if (fresh.containsKey(key)) {
                    plugin.getLogger().warning("alanlar.yml: '" + name + "' adı tekrar ediyor, ikincisi atlandı.");
                    continue;
                }
                fresh.put(key, zone);
            }
        }
        zones.clear();
        zones.putAll(fresh);
        rebuildIndex();
        lastError = null;
        if (!file.exists()) {
            String err = save();
            if (err != null) return err;
        } else {
            stamp = YamlIO.stamp(file);
        }
        return null;
    }

    /** Dosya elle değiştirildiyse komut işlemeden önce yeniden yükler. */
    private String ensureFresh() {
        if (file.exists() && YamlIO.changed(file, stamp)) return load();
        return null;
    }

    private String save() {
        YamlConfiguration y = new YamlConfiguration();
        y.createSection("alanlar");
        for (AfkZone z : zones.values()) {
            String p = "alanlar." + z.getName() + ".";
            y.set(p + "dunya", z.getWorld());
            y.set(p + "pos1.x", z.getMinX());
            y.set(p + "pos1.y", z.getMinY());
            y.set(p + "pos1.z", z.getMinZ());
            y.set(p + "pos2.x", z.getMaxX());
            y.set(p + "pos2.y", z.getMaxY());
            y.set(p + "pos2.z", z.getMaxZ());
            y.set(p + "cooldown", z.getCooldown());
            y.set(p + "odul", z.getReward());
            y.set(p + "vip-odul", z.getVipReward());
            y.set(p + "vip-yetki", z.getVipPermission());
        }
        try {
            YamlIO.write(file, HEADER, y);
            stamp = YamlIO.stamp(file);
            return null;
        } catch (Exception e) {
            String err = YamlIO.error(e);
            plugin.getLogger().severe("alanlar.yml kaydedilemedi: " + err);
            return err;
        }
    }

    private void rebuildIndex() {
        Map<String, List<AfkZone>> tmp = new HashMap<>();
        for (AfkZone z : zones.values()) {
            List<AfkZone> list = tmp.get(z.getWorld());
            if (list == null) tmp.put(z.getWorld(), list = new ArrayList<>(2));
            list.add(z);
        }
        Map<String, AfkZone[]> idx = new HashMap<>(Math.max(4, tmp.size() * 2));
        for (Map.Entry<String, List<AfkZone>> e : tmp.entrySet()) {
            idx.put(e.getKey(), e.getValue().toArray(new AfkZone[0]));
        }
        byWorld = idx;
    }

    public static boolean validName(String name) {
        return name != null && NAME.matcher(name).matches();
    }

    public Result create(String name, String world, int x1, int y1, int z1, int x2, int y2, int z2) {
        if (!validName(name)) return Result.INVALID_NAME;
        if (ensureFresh() != null) return Result.FILE_ERROR;
        String key = name.toLowerCase(Locale.ROOT);
        if (zones.containsKey(key)) return Result.EXISTS;
        Settings s = plugin.settings();
        zones.put(key, new AfkZone(name, world, x1, y1, z1, x2, y2, z2,
                s.defCooldown, s.defReward, s.defVipReward, s.defVipPerm));
        rebuildIndex();
        if (save() != null) {
            zones.remove(key);
            rebuildIndex();
            return Result.FILE_ERROR;
        }
        return Result.OK;
    }

    public Result delete(String name) {
        if (ensureFresh() != null) return Result.FILE_ERROR;
        String key = name.toLowerCase(Locale.ROOT);
        AfkZone old = zones.remove(key);
        if (old == null) return Result.NOT_FOUND;
        rebuildIndex();
        if (save() != null) {
            zones.put(key, old);
            rebuildIndex();
            return Result.FILE_ERROR;
        }
        return Result.OK;
    }

    public Result update(String name, int cooldown, double reward, double vipReward) {
        if (ensureFresh() != null) return Result.FILE_ERROR;
        String key = name.toLowerCase(Locale.ROOT);
        AfkZone old = zones.get(key);
        if (old == null) return Result.NOT_FOUND;
        zones.put(key, old.with(cooldown, reward, vipReward));
        rebuildIndex();
        if (save() != null) {
            zones.put(key, old);
            rebuildIndex();
            return Result.FILE_ERROR;
        }
        return Result.OK;
    }

    /** Oyuncunun bulunduğu alan; yoksa null. Ana thread, saniyede bir. */
    public AfkZone find(String world, double x, double y, double z) {
        AfkZone[] arr = byWorld.get(world);
        if (arr == null) return null;
        for (AfkZone zone : arr) {
            if (zone.contains(x, y, z)) return zone;
        }
        return null;
    }

    public AfkZone get(String name) {
        return name == null ? null : zones.get(name.toLowerCase(Locale.ROOT));
    }

    public Collection<AfkZone> all() {
        return Collections.unmodifiableCollection(zones.values());
    }

    public List<String> names() {
        List<String> out = new ArrayList<>(zones.size());
        for (AfkZone z : zones.values()) out.add(z.getName());
        return out;
    }

    public String lastError() {
        return lastError;
    }
}
