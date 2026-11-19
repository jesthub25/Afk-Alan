package com.jestron.afkalan.market;

import com.jestron.afkalan.AfkAlan;
import com.jestron.afkalan.Settings;
import com.jestron.afkalan.balance.BalanceManager;
import com.jestron.afkalan.util.Num;
import com.jestron.afkalan.util.Text;
import com.jestron.afkalan.util.YamlIO;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * afkmarket.yml ile birebir senkron AFK market.
 *
 * 45 slot (5 satır). Kenarların tamamı gri cam. Satılık ürünler 2. satır 2. slottan başlar:
 *   satır 2: slot 10-16, satır 3: slot 19-25, satır 4: slot 28-34  -> 21 ürün (#1 - #21)
 */
public final class MarketManager {

    public static final int SIZE = 45;
    public static final int SLOTS = 21;
    public static final String PERM_ADMIN = "afk.admin.market";

    private static final int[] RAW = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34};
    private static final int[] RAW_TO_SLOT = new int[SIZE];

    static {
        for (int i = 0; i < SIZE; i++) RAW_TO_SLOT[i] = -1;
        for (int i = 0; i < RAW.length; i++) RAW_TO_SLOT[RAW[i]] = i + 1;
    }

    private static final String HEADER =
            "AfkAlan - AFK market ürünleri (slot 1-21)\n"
                    + "Her ürün: fiyat ve item. Fiyatı buradan değiştirip /afk reload yazabilirsiniz.\n"
                    + "Ürün eklemek için oyunda itemi elinize alıp: /afk market ekle <fiyat>";

    public enum Result { OK, FULL, SLOT_TAKEN, SLOT_EMPTY, INVALID_SLOT, FILE_ERROR }

    public static final class Item {
        final ItemStack item;
        final double price;

        Item(ItemStack item, double price) {
            this.item = item;
            this.price = price;
        }

        public ItemStack item() {
            return item.clone();
        }

        public double price() {
            return price;
        }
    }

    private final AfkAlan plugin;
    private final File file;
    private final Item[] items = new Item[SLOTS + 1];
    private int version;
    private long[] stamp;
    private String lastError;

    private final Set<MarketMenu> open = new HashSet<>();
    private final Map<UUID, Long> lastBuy = new HashMap<>();
    private ItemStack glass;

    public MarketManager(AfkAlan plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "afkmarket.yml");
    }

    // ---------------------------------------------------------------- dosya

    /** @return null başarılı, aksi halde hata (eski veriler korunur) */
    public String load() {
        YamlConfiguration y;
        try {
            y = YamlIO.read(file);
        } catch (Exception e) {
            lastError = YamlIO.error(e);
            plugin.getLogger().severe("afkmarket.yml okunamadı, eski market korunuyor: " + lastError);
            return lastError;
        }
        Item[] fresh = new Item[SLOTS + 1];
        ConfigurationSection s = y.getConfigurationSection("urunler");
        if (s != null) {
            for (String key : s.getKeys(false)) {
                Integer slot = Num.parseInt(key);
                if (slot == null || slot < 1 || slot > SLOTS) {
                    plugin.getLogger().warning("afkmarket.yml: geçersiz slot '" + key + "' (1-21), atlandı.");
                    continue;
                }
                ItemStack it;
                try {
                    it = s.getItemStack(key + ".item");
                } catch (Exception ex) {
                    it = null;
                }
                if (it == null || it.getType() == Material.AIR || it.getAmount() < 1) {
                    plugin.getLogger().warning("afkmarket.yml: #" + slot + " item okunamadı, atlandı.");
                    continue;
                }
                double price = s.getDouble(key + ".fiyat", -1D);
                if (price <= 0D) {
                    plugin.getLogger().warning("afkmarket.yml: #" + slot + " fiyat 0'dan büyük olmalı, atlandı.");
                    continue;
                }
                fresh[slot] = new Item(it.clone(), Num.money(price));
            }
        }
        System.arraycopy(fresh, 0, items, 0, items.length);
        version++;
        lastError = null;
        if (!file.exists()) {
            String err = save();
            if (err != null) return err;
        } else {
            stamp = YamlIO.stamp(file);
        }
        refreshAll();
        return null;
    }

    private String ensureFresh() {
        if (file.exists() && YamlIO.changed(file, stamp)) return load();
        return null;
    }

    private String save() {
        YamlConfiguration y = new YamlConfiguration();
        y.createSection("urunler");
        for (int i = 1; i <= SLOTS; i++) {
            Item it = items[i];
            if (it == null) continue;
            y.set("urunler." + i + ".fiyat", it.price);
            y.set("urunler." + i + ".item", it.item);
        }
        try {
            YamlIO.write(file, HEADER, y);
            stamp = YamlIO.stamp(file);
            return null;
        } catch (Exception e) {
            String err = YamlIO.error(e);
            plugin.getLogger().severe("afkmarket.yml kaydedilemedi: " + err);
            return err;
        }
    }

    public String lastError() {
        return lastError;
    }

    // ---------------------------------------------------------------- admin

    /** @param slot null ise ilk boş slot. @return [Result, slot] */
    public Object[] add(ItemStack stack, double price, Integer slot) {
        if (ensureFresh() != null) return new Object[]{Result.FILE_ERROR, 0};
        int target;
        if (slot == null) {
            target = -1;
            for (int i = 1; i <= SLOTS; i++) {
                if (items[i] == null) {
                    target = i;
                    break;
                }
            }
            if (target < 0) return new Object[]{Result.FULL, 0};
        } else {
            if (slot < 1 || slot > SLOTS) return new Object[]{Result.INVALID_SLOT, slot};
            if (items[slot] != null) return new Object[]{Result.SLOT_TAKEN, slot};
            target = slot;
        }
        items[target] = new Item(stack.clone(), Num.money(price));
        if (save() != null) {
            items[target] = null;
            return new Object[]{Result.FILE_ERROR, target};
        }
        changed();
        return new Object[]{Result.OK, target};
    }

    public Result remove(int slot) {
        if (slot < 1 || slot > SLOTS) return Result.INVALID_SLOT;
        if (ensureFresh() != null) return Result.FILE_ERROR;
        Item old = items[slot];
        if (old == null) return Result.SLOT_EMPTY;
        items[slot] = null;
        if (save() != null) {
            items[slot] = old;
            return Result.FILE_ERROR;
        }
        changed();
        return Result.OK;
    }

    public Result setPrice(int slot, double price) {
        if (slot < 1 || slot > SLOTS) return Result.INVALID_SLOT;
        if (ensureFresh() != null) return Result.FILE_ERROR;
        Item old = items[slot];
        if (old == null) return Result.SLOT_EMPTY;
        items[slot] = new Item(old.item, Num.money(price));
        if (save() != null) {
            items[slot] = old;
            return Result.FILE_ERROR;
        }
        changed();
        return Result.OK;
    }

    private void changed() {
        version++;
        refreshAll();
    }

    public List<String> filledSlots() {
        List<String> out = new ArrayList<>();
        for (int i = 1; i <= SLOTS; i++) if (items[i] != null) out.add(String.valueOf(i));
        return out;
    }

    public List<String> emptySlots() {
        List<String> out = new ArrayList<>();
        for (int i = 1; i <= SLOTS; i++) if (items[i] == null) out.add(String.valueOf(i));
        return out;
    }

    public Item get(int slot) {
        return slot < 1 || slot > SLOTS ? null : items[slot];
    }

    // ---------------------------------------------------------------- GUI

    public void open(Player player) {
        MarketMenu menu = new MarketMenu(player.getUniqueId(), plugin.settings().marketTitle);
        render(menu, player);
        player.openInventory(menu.getInventory());
        open.add(menu);
    }

    void onClose(MarketMenu menu) {
        open.remove(menu);
    }

    /** Market değişti / bakiye değişti: açık menüleri yeniler. */
    public void refreshAll() {
        if (open.isEmpty()) return;
        for (MarketMenu m : new ArrayList<>(open)) {
            Player p = Bukkit.getPlayer(m.getViewer());
            if (p == null) {
                open.remove(m);
                continue;
            }
            render(m, p);
        }
    }

    public void refreshViewer(UUID uuid) {
        if (open.isEmpty()) return;
        for (MarketMenu m : new ArrayList<>(open)) {
            if (!m.getViewer().equals(uuid)) continue;
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) render(m, p);
        }
    }

    public void closeAll() {
        for (MarketMenu m : new ArrayList<>(open)) {
            Player p = Bukkit.getPlayer(m.getViewer());
            if (p != null) p.closeInventory();
        }
        open.clear();
    }

    public void forget(UUID uuid) {
        lastBuy.remove(uuid);
        if (!open.isEmpty()) open.removeIf(m -> m.getViewer().equals(uuid));
    }

    int openCount() {
        return open.size();
    }

    /** viewer null olabilir (test). */
    void render(MarketMenu menu, Player viewer) {
        Inventory inv = menu.getInventory();
        if (!menu.framed) {
            ItemStack g = glass();
            for (int raw = 0; raw < SIZE; raw++) {
                if (RAW_TO_SLOT[raw] < 0) inv.setItem(raw, g);
            }
            menu.framed = true;
        }
        Settings s = plugin.settings();
        double bal = viewer == null ? 0D : plugin.balances().get(viewer.getUniqueId());
        boolean admin = viewer != null && viewer.hasPermission(PERM_ADMIN);
        String balText = Num.format(bal);
        for (int i = 0; i < RAW.length; i++) {
            int slot = i + 1;
            Item it = items[slot];
            inv.setItem(RAW[i], it == null ? null : display(it, slot, bal, balText, admin, s));
        }
        menu.version = version;
    }

    private ItemStack display(Item it, int slot, double bal, String balText, boolean admin, Settings s) {
        ItemStack d = it.item.clone();
        ItemMeta meta = d.getItemMeta();
        if (meta == null) return d;
        List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        String price = Num.format(it.price);
        String state = Num.gte(bal, it.price) ? s.marketOk : s.marketNo;
        String slotText = String.valueOf(slot);
        for (String line : s.marketLore) {
            lore.add(Text.replace(line, "{fiyat}", price, "{bakiye}", balText, "{para}", s.currency,
                    "{durum}", state, "{slot}", slotText));
        }
        if (admin && !s.marketAdmin.isEmpty()) lore.add(Text.replace(s.marketAdmin, "{slot}", slotText));
        meta.setLore(lore);
        d.setItemMeta(meta);
        return d;
    }

    private ItemStack glass() {
        if (glass == null) {
            ItemStack g = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta m = g.getItemMeta();
            if (m != null) {
                m.setDisplayName(" ");
                g.setItemMeta(m);
            }
            glass = g;
        }
        return glass;
    }

    /** -1 = satılık slot değil */
    static int slotOf(int raw) {
        return raw < 0 || raw >= SIZE ? -1 : RAW_TO_SLOT[raw];
    }

    // ---------------------------------------------------------------- satın alma

    void purchase(Player p, MarketMenu menu, int slot) {
        Settings s = plugin.settings();
        UUID uuid = p.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = lastBuy.get(uuid);
        if (last != null && now - last < s.buyCooldownMs) return;
        lastBuy.put(uuid, now);

        if (menu.version != version) {
            render(menu, p);
            s.send(p, "market-guncellendi");
            return;
        }
        Item it = items[slot];
        if (it == null) return;

        BalanceManager bm = plugin.balances();
        double bal = bm.get(uuid);
        if (!Num.gte(bal, it.price)) {
            s.send(p, "market-yetersiz", "{fiyat}", Num.format(it.price), "{bakiye}", Num.format(bal));
            plugin.playSound(p, s.soundFail);
            return;
        }
        ItemStack give = it.item.clone();
        if (!canFit(p.getInventory(), give)) {
            s.send(p, "market-envanter-dolu");
            plugin.playSound(p, s.soundFail);
            return;
        }
        if (!bm.take(uuid, it.price)) {
            s.send(p, "market-yetersiz", "{fiyat}", Num.format(it.price), "{bakiye}", Num.format(bm.get(uuid)));
            return;
        }
        Map<Integer, ItemStack> left = p.getInventory().addItem(give);
        for (ItemStack rest : left.values()) {
            if (rest != null && rest.getType() != Material.AIR) p.getWorld().dropItemNaturally(p.getLocation(), rest);
        }
        bm.requestSave();
        s.send(p, "market-satin-alindi",
                "{adet}", String.valueOf(give.getAmount()),
                "{item}", itemName(give),
                "{fiyat}", Num.format(it.price),
                "{bakiye}", Num.format(bm.get(uuid)));
        plugin.playSound(p, s.soundBuy);
        refreshViewer(uuid);
    }

    static boolean canFit(PlayerInventory inv, ItemStack item) {
        int need = item.getAmount();
        int max = Math.max(1, item.getMaxStackSize());
        for (ItemStack s : inv.getStorageContents()) {
            if (s == null || s.getType() == Material.AIR) {
                need -= max;
            } else if (s.isSimilar(item)) {
                need -= Math.max(0, max - s.getAmount());
            }
            if (need <= 0) return true;
        }
        return need <= 0;
    }

    static String itemName(ItemStack it) {
        ItemMeta m = it.getItemMeta();
        if (m != null && m.hasDisplayName()) return m.getDisplayName();
        return Text.prettyMaterial(it.getType().name());
    }
}
