package com.jestron.afkalan;

import com.jestron.afkalan.display.AfkBoard;
import com.jestron.afkalan.market.MarketManager;
import com.jestron.afkalan.util.Num;
import com.jestron.afkalan.util.Text;
import com.jestron.afkalan.util.YamlIO;
import com.jestron.afkalan.zone.AfkZone;
import com.jestron.afkalan.zone.ZoneManager;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Sadece -Dafkalan.selftest=true ile başlatılan sunucularda (CI) çalışır.
 * Normal sunucularda hiçbir zaman çalışmaz. Sürüme bağlı tüm API'leri gerçek sunucuda dener.
 */
final class SelfTest {

    private final AfkAlan plugin;
    private final List<String> failures = new ArrayList<>();
    private int passed;

    SelfTest(AfkAlan plugin) {
        this.plugin = plugin;
    }

    void run() {
        String version = Bukkit.getVersion();
        log("Başlıyor: " + version);
        step("metin", this::text);
        step("api-imzalari", this::apiSignatures);
        step("alan", this::zones);
        step("market", this::market);
        step("bakiye", this::balances);
        step("scoreboard", this::scoreboard);
        step("komutlar", this::commands);
        step("reload", () -> plugin.reload(Bukkit.getConsoleSender()));
        if (Bukkit.getPluginManager().getPlugin("WorldEdit") != null
                || Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit") != null) {
            step("worldedit", () -> check(plugin.worldEdit().ready(), "WorldEdit bağlantısı kurulamadı"));
        }
        if (plugin.papiEnabled()) {
            step("placeholderapi", this::papi);
        }

        if (failures.isEmpty()) {
            log("AFKALAN-SELFTEST: OK (" + passed + " adım)");
        } else {
            for (String f : failures) log("HATA: " + f);
            log("AFKALAN-SELFTEST: FAIL (" + failures.size() + " hata)");
        }
        if (System.getProperty("afkalan.selftest.shutdown") != null) {
            Bukkit.getScheduler().runTaskLater(plugin, Bukkit::shutdown, 20L);
        }
    }

    private interface Step {
        void run() throws Exception;
    }

    private void step(String name, Step s) {
        try {
            s.run();
            passed++;
            log("✓ " + name);
        } catch (Throwable t) {
            failures.add(name + ": " + t);
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "[SELFTEST] " + name, t);
        }
    }

    private static void check(boolean ok, String msg) {
        if (!ok) throw new IllegalStateException(msg);
    }

    private void log(String m) {
        plugin.getLogger().info("[SELFTEST] " + m);
    }

    // ------------------------------------------------------------------ adımlar

    private void text() {
        String c = Text.color("&#FFAA00Merhaba &aDünya");
        check(c.contains("§x§f§f§a§a§0§0"), "hex renk çevrilmedi: " + c);
        String lim = Text.limit("§x§f§f§a§a§0§0abc", 10);
        check(lim.isEmpty(), "yarım hex kırpılmadı: " + lim);
        check(Num.format(12) .equals("12"), "format 12");
        check(Num.format(12.5).equals("12.50"), "format 12.5");
        check(Num.format(0.1 + 0.2).equals("0.30"), "format 0.3");
        check(Num.parse("1,5") == 1.5D, "parse 1,5");
    }

    private void apiSignatures() throws Exception {
        Player.class.getMethod("sendTitle", String.class, String.class, int.class, int.class, int.class);
        Player.class.getMethod("resetTitle");
        Player.class.getMethod("playSound", Location.class, String.class, float.class, float.class);
        Player.Spigot.class.getMethod("sendMessage", ChatMessageType.class, BaseComponent[].class);
        Player.class.getMethod("getLocation", Location.class);
        new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
    }

    private World world() {
        List<World> worlds = Bukkit.getWorlds();
        check(!worlds.isEmpty(), "dünya yok");
        return worlds.get(0);
    }

    private void zones() {
        ZoneManager zm = plugin.zones();
        String w = world().getName();
        if (zm.get("selftest") != null) zm.delete("selftest");
        check(zm.create("selftest", w, 10, 60, 10, 0, 64, 0) == ZoneManager.Result.OK, "oluşturulamadı");
        check(zm.create("SELFTEST", w, 0, 0, 0, 1, 1, 1) == ZoneManager.Result.EXISTS, "aynı isim kabul edildi");
        check(zm.create("bad.name", w, 0, 0, 0, 1, 1, 1) == ZoneManager.Result.INVALID_NAME, "geçersiz isim kabul edildi");
        AfkZone z = zm.find(w, 5.5, 62.0, 5.5);
        check(z != null && z.getName().equals("selftest"), "içeride bulunamadı");
        check(zm.find(w, 10.99, 64.99, 10.99) != null, "sınır bloğu dahil değil");
        check(zm.find(w, 11.0, 62, 5) == null, "dışarısı içeride sayıldı");
        check(zm.find("yok_dunya", 5, 62, 5) == null, "başka dünya");
        check(zm.update("selftest", 30, 2.5, 5) == ZoneManager.Result.OK, "ayarlanamadı");
        check(zm.load() == null, "yeniden yüklenemedi");
        AfkZone r = zm.get("selftest");
        check(r != null && r.getCooldown() == 30 && r.getReward() == 2.5 && r.getVipReward() == 5D, "ayarlar dosyaya yazılmadı");
        check(zm.delete("selftest") == ZoneManager.Result.OK, "silinemedi");
        check(zm.get("selftest") == null, "silinmedi");
    }

    private void market() throws Exception {
        MarketManager mm = plugin.market();
        for (String s : mm.filledSlots()) mm.remove(Integer.parseInt(s));
        ItemStack it = new ItemStack(Material.DIAMOND, 3);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(Text.color("&bÖzel Elmas"));
        meta.setLore(Arrays.asList(Text.color("&7Test"), "satır 2"));
        it.setItemMeta(meta);

        Object[] r = mm.add(it, 5, null);
        check(r[0] == MarketManager.Result.OK && ((Integer) r[1]) == 1, "eklenemedi: " + r[0]);
        check(mm.add(it, 5, 1)[0] == MarketManager.Result.SLOT_TAKEN, "dolu slot kabul edildi");
        check(mm.add(it, 7, 21)[0] == MarketManager.Result.OK, "slot 21");
        check(mm.load() == null, "yeniden yüklenemedi");
        check(mm.get(1) != null && mm.get(1).item().isSimilar(it) && mm.get(1).item().getAmount() == 3, "item dosyadan bozuk döndü");
        check(mm.setPrice(1, 9.99) == MarketManager.Result.OK && mm.get(1).price() == 9.99, "fiyat");
        check(mm.filledSlots().equals(Arrays.asList("1", "21")), "tab listesi: " + mm.filledSlots());
        check(MarketManager.class.getDeclaredMethod("render", Class.forName("com.jestron.afkalan.market.MarketMenu"), Player.class) != null, "render");
        MarketTestAccess.renderCheck(mm);
        check(mm.remove(1) == MarketManager.Result.OK && mm.remove(21) == MarketManager.Result.OK, "silinemedi");
        check(mm.remove(1) == MarketManager.Result.SLOT_EMPTY, "boş slot silindi");
        check(mm.remove(22) == MarketManager.Result.INVALID_SLOT, "slot 22");
    }

    private void balances() throws Exception {
        // not: elle düzenleme dosyanın değiştirilme zamanından bağımsız algılanır (her kayıt dosyayı okuyup birleştirir)
        UUID u = UUID.nameUUIDFromBytes("afkalan-selftest".getBytes("UTF-8"));
        com.jestron.afkalan.balance.BalanceManager bm = plugin.balances();
        bm.set(u, "SelfTestOyuncu", 0);
        check(bm.add(u, "SelfTestOyuncu", 10.5) == 10.5, "ekleme");
        check(bm.take(u, 0.5) && bm.get(u) == 10D, "düşme");
        check(!bm.take(u, 100), "yetersiz bakiye düşüldü");
        check(bm.add(u, null, -50) == 0D, "negatife indi");
        bm.set(u, null, 10);
        check(u.equals(bm.findByName("selftestoyuncu")), "isimden bulma");

        // kaydet -> dosya elle düzenlenir -> bekleyen farkla birleşir
        bm.saveAsync();
        waitIo(bm);
        File f = new File(plugin.getDataFolder(), "bakiyeler.yml");
        YamlConfiguration y = YamlIO.read(f);
        check(y.getDouble("oyuncular." + u + ".bakiye") == 10D, "dosyaya yazılmadı: " + y.getDouble("oyuncular." + u + ".bakiye"));
        y.set("oyuncular." + u + ".bakiye", 100D); // elle düzenleme
        YamlIO.write(f, null, y);
        bm.add(u, null, 1); // kaydedilmemiş fark
        check(bm.reload() == null, "reload");
        check(bm.get(u) == 101D, "elle düzenleme + fark birleşmedi: " + bm.get(u));
        bm.set(u, null, 0);
        bm.saveAsync();
        waitIo(bm);
    }

    private void waitIo(com.jestron.afkalan.balance.BalanceManager bm) {
        // reload() IO kuyruğunu bekler; okunan değer önemli değil
        bm.reload();
    }

    private void scoreboard() {
        String[] lines = {Text.color("&#FFAA00&lUzun bir satır &a{bakiye} &bdeneme deneme deneme deneme deneme"), "", " ", "x"};
        AfkBoard b = AfkBoard.build(null, Text.color("&6&lAFK"), lines.length, true);
        check(b != null, "board kurulamadı");
        for (int i = 0; i < lines.length; i++) b.line(i, lines[i]);
        b.line(0, lines[0]); // değişmeyen satır
        b.title(Text.color("&eYeni Başlık"));
        check(b.scoreboard().getObjective("afkalan") != null, "objective yok");
        check(b.scoreboard().getTeams().size() == lines.length, "takım sayısı");
    }

    private void commands() {
        UUID u = UUID.nameUUIDFromBytes("afkalan-selftest".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        plugin.balances().set(u, "SelfTestOyuncu", 0);
        CommandRunner.run("afk para ver SelfTestOyuncu 5");
        check(plugin.balances().get(u) == 5D, "para ver: " + plugin.balances().get(u));
        CommandRunner.run("afk para al SelfTestOyuncu 2");
        check(plugin.balances().get(u) == 3D, "para al");
        CommandRunner.run("afk para ayarla SelfTestOyuncu 42.42");
        check(plugin.balances().get(u) == 42.42D, "para ayarla");
        CommandRunner.run("afk para bak SelfTestOyuncu");
        CommandRunner.run("afk top");
        CommandRunner.run("afk alan liste");
        CommandRunner.run("afk market sil 99");
        CommandRunner.run("afk yardim");
        CommandRunner.run("afkalan");
        List<String> tab = plugin.getCommand("afk").tabComplete(Bukkit.getConsoleSender(), "afk", new String[]{"p"});
        check(tab.contains("para"), "tab: " + tab);
        plugin.balances().set(u, null, 0);
    }

    private void papi() {
        String v = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders((Player) null, "%afk_para_birimi%");
        check(v.equals(plugin.settings().currency), "%afk_para_birimi% -> " + v);
    }

    /** Konsoldan komut çalıştırır. */
    private static final class CommandRunner {
        static void run(String cmd) {
            if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd)) throw new IllegalStateException("komut çalışmadı: " + cmd);
        }
    }

    /** Market GUI'sini oyuncusuz oluşturup kontrol eder. */
    private static final class MarketTestAccess {
        static void renderCheck(MarketManager mm) throws Exception {
            Class<?> menuClass = Class.forName("com.jestron.afkalan.market.MarketMenu");
            java.lang.reflect.Constructor<?> c = menuClass.getDeclaredConstructor(UUID.class, String.class);
            c.setAccessible(true);
            Object menu = c.newInstance(UUID.randomUUID(), "Test");
            java.lang.reflect.Method render = MarketManager.class.getDeclaredMethod("render", menuClass, Player.class);
            render.setAccessible(true);
            render.invoke(mm, menu, null);
            org.bukkit.inventory.Inventory inv = ((org.bukkit.inventory.InventoryHolder) menu).getInventory();
            check(inv.getSize() == 45, "boyut");
            for (int raw : new int[]{0, 8, 9, 17, 18, 26, 27, 35, 36, 44, 40}) {
                ItemStack g = inv.getItem(raw);
                check(g != null && g.getType() == Material.GRAY_STAINED_GLASS_PANE, "kenar camı yok: " + raw);
            }
            ItemStack first = inv.getItem(10);
            check(first != null && first.getType() == Material.DIAMOND, "ilk ürün 2. satır 2. slotta değil");
            ItemStack last = inv.getItem(34);
            check(last != null && last.getType() == Material.DIAMOND, "#21 slot 34'te değil");
            check(inv.getItem(11) == null, "boş slot dolu");
            List<String> lore = first.getItemMeta().getLore();
            check(lore != null && lore.size() > 2 && lore.get(0).contains("Test"), "lore korunmadı: " + lore);
            check(MarketManager.class.getDeclaredMethod("slotOf", int.class) != null, "slotOf");
        }
    }
}
