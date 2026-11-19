package com.jestron.afkalan.command;

import com.jestron.afkalan.AfkAlan;
import com.jestron.afkalan.Settings;
import com.jestron.afkalan.balance.BalanceManager;
import com.jestron.afkalan.hook.WorldEditHook;
import com.jestron.afkalan.market.MarketManager;
import com.jestron.afkalan.util.Num;
import com.jestron.afkalan.util.Text;
import com.jestron.afkalan.zone.AfkZone;
import com.jestron.afkalan.zone.ZoneManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class AfkCommand implements CommandExecutor, TabCompleter {

    public static final String P_USE = "afk.use";
    public static final String P_ALAN = "afk.admin.alan";
    public static final String P_MARKET = MarketManager.PERM_ADMIN;
    public static final String P_PARA = "afk.admin.para";
    public static final String P_RELOAD = "afk.admin.reload";

    private static final int MAX_COOLDOWN = 86_400;

    private final AfkAlan plugin;

    public AfkCommand(AfkAlan plugin) {
        this.plugin = plugin;
    }

    private Settings s() {
        return plugin.settings();
    }

    private static String low(String s) {
        return s.toLowerCase(Locale.ROOT);
    }

    // ======================================================================= komutlar

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender, label);
            return true;
        }
        switch (low(args[0])) {
            case "bakiye":
                bakiye(sender);
                break;
            case "market":
                market(sender, label, args);
                break;
            case "top":
            case "sira":
            case "sıralama":
                top(sender);
                break;
            case "alan":
                alan(sender, label, args);
                break;
            case "para":
                para(sender, label, args);
                break;
            case "reload":
            case "yenile":
                if (!perm(sender, P_RELOAD)) return true;
                plugin.reload(sender);
                break;
            default:
                help(sender, label);
        }
        return true;
    }

    private boolean perm(CommandSender sender, String perm) {
        if (sender.hasPermission(perm)) return true;
        s().send(sender, "yetki-yok");
        return false;
    }

    private Player player(CommandSender sender) {
        if (sender instanceof Player) return (Player) sender;
        s().send(sender, "sadece-oyuncu");
        return null;
    }

    private void help(CommandSender sender, String label) {
        String c = "/" + label;
        List<String> lines = new ArrayList<>();
        lines.add("&6&lAfkAlan &7v" + plugin.getDescription().getVersion());
        if (sender.hasPermission(P_USE)) {
            lines.add("&e" + c + " bakiye &8- &7AFK paranı gösterir");
            lines.add("&e" + c + " market &8- &7AFK marketi açar");
            lines.add("&e" + c + " top &8- &7En zengin AFK oyuncuları");
        }
        if (sender.hasPermission(P_ALAN)) {
            lines.add("&c" + c + " alan oluştur <isim> &8- &7Seçili pos1-pos2 alanını kaydeder");
            lines.add("&c" + c + " alan sil <isim>");
            lines.add("&c" + c + " alan liste");
            lines.add("&c" + c + " alan ayarla <isim> <cooldown|odul|vip-odul> <değer>");
        }
        if (sender.hasPermission(P_MARKET)) {
            lines.add("&c" + c + " market ekle <fiyat> [slot] &8- &7Elindeki itemi ekler");
            lines.add("&c" + c + " market sil <slot>");
            lines.add("&c" + c + " market fiyat <slot> <fiyat>");
        }
        if (sender.hasPermission(P_PARA)) {
            lines.add("&c" + c + " para ver|al|ayarla <oyuncu> <miktar>");
            lines.add("&c" + c + " para bak <oyuncu>");
        }
        if (sender.hasPermission(P_RELOAD)) lines.add("&c" + c + " reload &8- &7Tüm .yml dosyalarını yeniden yükler");
        for (String l : lines) sender.sendMessage(Text.color(l));
    }

    // ---------------------------------------------------------------- bakiye / top

    private void bakiye(CommandSender sender) {
        if (!perm(sender, P_USE)) return;
        Player p = player(sender);
        if (p == null) return;
        s().send(p, "bakiye", "{bakiye}", Num.format(plugin.balances().get(p.getUniqueId())));
    }

    private void top(CommandSender sender) {
        if (!perm(sender, P_USE)) return;
        List<BalanceManager.TopEntry> list = plugin.balances().top(10);
        if (list.isEmpty()) {
            s().send(sender, "top-bos");
            return;
        }
        s().send(sender, "top-baslik");
        for (int i = 0; i < list.size(); i++) {
            BalanceManager.TopEntry e = list.get(i);
            s().send(sender, "top-satir", "{sira}", String.valueOf(i + 1), "{oyuncu}", e.name, "{bakiye}", Num.format(e.balance));
        }
    }

    // ---------------------------------------------------------------- market

    private void market(CommandSender sender, String label, String[] args) {
        MarketManager mm = plugin.market();
        if (args.length == 1) {
            if (!perm(sender, P_USE)) return;
            Player p = player(sender);
            if (p != null) mm.open(p);
            return;
        }
        String sub = low(args[1]);
        if (!sub.equals("ekle") && !sub.equals("sil") && !sub.equals("fiyat")) {
            if (sender.hasPermission(P_MARKET)) {
                s().usage(sender, "/" + label + " market [ekle <fiyat> [slot] | sil <slot> | fiyat <slot> <fiyat>]");
            } else if (perm(sender, P_USE)) {
                Player p = player(sender);
                if (p != null) mm.open(p);
            }
            return;
        }
        if (!perm(sender, P_MARKET)) return;

        if (sub.equals("ekle")) {
            Player p = player(sender);
            if (p == null) return;
            if (args.length < 3) {
                s().usage(p, "/" + label + " market ekle <fiyat> [slot]");
                return;
            }
            Double price = positive(p, args[2]);
            if (price == null) return;
            Integer slot = null;
            if (args.length >= 4) {
                slot = Num.parseInt(args[3]);
                if (slot == null || slot < 1 || slot > MarketManager.SLOTS) {
                    s().send(p, "gecersiz-slot");
                    return;
                }
            }
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand == null || hand.getType() == Material.AIR || hand.getAmount() < 1) {
                s().send(p, "market-elde-item-yok");
                return;
            }
            Object[] r = mm.add(hand, price, slot);
            String slotText = String.valueOf(r[1]);
            switch ((MarketManager.Result) r[0]) {
                case OK:
                    s().send(p, "market-eklendi", "{slot}", slotText, "{fiyat}", Num.format(price));
                    break;
                case FULL:
                    s().send(p, "market-dolu");
                    break;
                case SLOT_TAKEN:
                    s().send(p, "market-slot-dolu", "{slot}", slotText);
                    break;
                case INVALID_SLOT:
                    s().send(p, "gecersiz-slot");
                    break;
                default:
                    fileError(p, "afkmarket.yml", mm.lastError());
            }
            return;
        }

        if (sub.equals("sil")) {
            if (args.length < 3) {
                s().usage(sender, "/" + label + " market sil <slot>");
                return;
            }
            Integer slot = slot(sender, args[2]);
            if (slot == null) return;
            MarketManager.Result r = mm.remove(slot);
            if (r == MarketManager.Result.OK) s().send(sender, "market-silindi", "{slot}", String.valueOf(slot));
            else if (r == MarketManager.Result.SLOT_EMPTY) s().send(sender, "market-slot-bos", "{slot}", String.valueOf(slot));
            else if (r == MarketManager.Result.INVALID_SLOT) s().send(sender, "gecersiz-slot");
            else fileError(sender, "afkmarket.yml", mm.lastError());
            return;
        }

        // fiyat
        if (args.length < 4) {
            s().usage(sender, "/" + label + " market fiyat <slot> <fiyat>");
            return;
        }
        Integer slot = slot(sender, args[2]);
        if (slot == null) return;
        Double price = positive(sender, args[3]);
        if (price == null) return;
        MarketManager.Result r = mm.setPrice(slot, price);
        if (r == MarketManager.Result.OK) s().send(sender, "market-fiyat", "{slot}", String.valueOf(slot), "{fiyat}", Num.format(price));
        else if (r == MarketManager.Result.SLOT_EMPTY) s().send(sender, "market-slot-bos", "{slot}", String.valueOf(slot));
        else if (r == MarketManager.Result.INVALID_SLOT) s().send(sender, "gecersiz-slot");
        else fileError(sender, "afkmarket.yml", mm.lastError());
    }

    private Integer slot(CommandSender sender, String raw) {
        Integer slot = Num.parseInt(raw);
        if (slot == null || slot < 1 || slot > MarketManager.SLOTS) {
            s().send(sender, "gecersiz-slot");
            return null;
        }
        return slot;
    }

    // ---------------------------------------------------------------- alan

    private void alan(CommandSender sender, String label, String[] args) {
        if (!perm(sender, P_ALAN)) return;
        String usage = "/" + label + " alan <oluştur|sil|liste|ayarla>";
        if (args.length < 2) {
            s().usage(sender, usage);
            return;
        }
        ZoneManager zm = plugin.zones();
        switch (low(args[1])) {
            case "oluştur":
            case "olustur":
            case "ekle":
            case "create": {
                Player p = player(sender);
                if (p == null) return;
                if (args.length < 3) {
                    s().usage(p, "/" + label + " alan oluştur <isim>");
                    return;
                }
                String name = args[2];
                if (!ZoneManager.validName(name)) {
                    s().send(p, "alan-isim-gecersiz");
                    return;
                }
                if (zm.get(name) != null) {
                    s().send(p, "alan-zaten-var", "{alan}", name);
                    return;
                }
                WorldEditHook.Selection sel = plugin.worldEdit().selection(p);
                if (sel.status == WorldEditHook.Status.NOT_INSTALLED) {
                    s().send(p, "worldedit-yok");
                    return;
                }
                if (sel.status != WorldEditHook.Status.OK) {
                    s().send(p, "pos-secilmedi");
                    return;
                }
                ZoneManager.Result r = zm.create(name, p.getWorld().getName(),
                        sel.minX, sel.minY, sel.minZ, sel.maxX, sel.maxY, sel.maxZ);
                if (r == ZoneManager.Result.OK) {
                    AfkZone z = zm.get(name);
                    s().send(p, "alan-olusturuldu", "{alan}", z.getName(),
                            "{boyut}", String.valueOf(z.volume()),
                            "{cooldown}", String.valueOf(z.getCooldown()),
                            "{odul}", Num.format(z.getReward()),
                            "{vip}", Num.format(z.getVipReward()));
                } else if (r == ZoneManager.Result.EXISTS) {
                    s().send(p, "alan-zaten-var", "{alan}", name);
                } else if (r == ZoneManager.Result.INVALID_NAME) {
                    s().send(p, "alan-isim-gecersiz");
                } else {
                    fileError(p, "alanlar.yml", zm.lastError());
                }
                return;
            }
            case "sil":
            case "kaldir":
            case "kaldır": {
                if (args.length < 3) {
                    s().usage(sender, "/" + label + " alan sil <isim>");
                    return;
                }
                AfkZone z = zm.get(args[2]);
                String name = z == null ? args[2] : z.getName();
                ZoneManager.Result r = zm.delete(args[2]);
                if (r == ZoneManager.Result.OK) {
                    plugin.tracker().resync(false);
                    s().send(sender, "alan-silindi", "{alan}", name);
                } else if (r == ZoneManager.Result.NOT_FOUND) {
                    s().send(sender, "alan-yok", "{alan}", name);
                } else {
                    fileError(sender, "alanlar.yml", zm.lastError());
                }
                return;
            }
            case "liste":
            case "list": {
                Collection<AfkZone> all = zm.all();
                if (all.isEmpty()) {
                    s().send(sender, "alan-liste-bos");
                    return;
                }
                s().send(sender, "alan-liste-baslik", "{sayi}", String.valueOf(all.size()));
                for (AfkZone z : all) {
                    s().send(sender, "alan-liste-satir",
                            "{alan}", z.getName(),
                            "{dunya}", z.getWorld(),
                            "{pos1}", z.getMinX() + "," + z.getMinY() + "," + z.getMinZ(),
                            "{pos2}", z.getMaxX() + "," + z.getMaxY() + "," + z.getMaxZ(),
                            "{cooldown}", String.valueOf(z.getCooldown()),
                            "{odul}", Num.format(z.getReward()),
                            "{vip}", Num.format(z.getVipReward()));
                }
                return;
            }
            case "ayarla":
            case "set": {
                if (args.length < 5) {
                    s().usage(sender, "/" + label + " alan ayarla <isim> <cooldown|odul|vip-odul> <değer>");
                    return;
                }
                AfkZone z = zm.get(args[2]);
                if (z == null) {
                    s().send(sender, "alan-yok", "{alan}", args[2]);
                    return;
                }
                String key = low(args[3]);
                Double v = Num.parse(args[4]);
                if (v == null) {
                    s().send(sender, "gecersiz-sayi", "{deger}", args[4]);
                    return;
                }
                int cd = z.getCooldown();
                double odul = z.getReward(), vip = z.getVipReward();
                String shown;
                switch (key) {
                    case "cooldown":
                    case "sure":
                    case "süre":
                        if (v < 1 || v > MAX_COOLDOWN || v != Math.floor(v)) {
                            s().send(sender, "gecersiz-sayi", "{deger}", args[4]);
                            return;
                        }
                        cd = (int) (double) v;
                        shown = String.valueOf(cd);
                        key = "cooldown";
                        break;
                    case "odul":
                    case "ödül":
                        if (v < 0) {
                            s().send(sender, "gecersiz-sayi", "{deger}", args[4]);
                            return;
                        }
                        odul = Num.money(v);
                        shown = Num.format(odul);
                        key = "odul";
                        break;
                    case "vip-odul":
                    case "vipodul":
                    case "vip-ödül":
                        if (v < 0) {
                            s().send(sender, "gecersiz-sayi", "{deger}", args[4]);
                            return;
                        }
                        vip = Num.money(v);
                        shown = Num.format(vip);
                        key = "vip-odul";
                        break;
                    default:
                        s().send(sender, "alan-ayar-gecersiz");
                        return;
                }
                ZoneManager.Result r = zm.update(z.getName(), cd, odul, vip);
                if (r == ZoneManager.Result.OK) {
                    plugin.tracker().resync(false);
                    s().send(sender, "alan-ayarlandi", "{alan}", z.getName(), "{ayar}", key, "{deger}", shown);
                } else if (r == ZoneManager.Result.NOT_FOUND) {
                    s().send(sender, "alan-yok", "{alan}", z.getName());
                } else {
                    fileError(sender, "alanlar.yml", zm.lastError());
                }
                return;
            }
            default:
                s().usage(sender, usage);
        }
    }

    // ---------------------------------------------------------------- para

    private void para(CommandSender sender, String label, String[] args) {
        if (!perm(sender, P_PARA)) return;
        String usage = "/" + label + " para <ver|al|ayarla|bak> <oyuncu> [miktar]";
        if (args.length < 3) {
            s().usage(sender, usage);
            return;
        }
        String sub = low(args[1]);
        if (!sub.equals("ver") && !sub.equals("al") && !sub.equals("ayarla") && !sub.equals("bak")) {
            s().usage(sender, usage);
            return;
        }
        // hedef oyuncu: önce çevrimiçi, sonra eklentinin tanıdığı oyuncular
        Player online = Bukkit.getPlayerExact(args[2]);
        UUID uuid;
        String name;
        String nameForFile = null;
        if (online != null) {
            uuid = online.getUniqueId();
            name = online.getName();
            nameForFile = name;
        } else {
            uuid = plugin.balances().findByName(args[2]);
            if (uuid != null) {
                String known = plugin.balances().name(uuid);
                name = known == null ? args[2] : known;
            } else {
                // Sunucuya daha önce girmiş ama henüz AFK parası olmayan oyuncu (ağ isteği yapılmaz)
                OfflinePlayer op = cachedOffline(args[2]);
                if (op == null) {
                    s().send(sender, "oyuncu-bulunamadi", "{oyuncu}", args[2]);
                    return;
                }
                uuid = op.getUniqueId();
                name = op.getName() == null ? args[2] : op.getName();
                nameForFile = name;
            }
        }
        BalanceManager bm = plugin.balances();
        if (sub.equals("bak")) {
            s().send(sender, "para-bak", "{oyuncu}", name, "{bakiye}", Num.format(bm.get(uuid)));
            return;
        }
        if (args.length < 4) {
            s().usage(sender, "/" + label + " para " + sub + " <oyuncu> <miktar>");
            return;
        }
        Double amount = Num.parse(args[3]);
        if (amount == null) {
            s().send(sender, "gecersiz-sayi", "{deger}", args[3]);
            return;
        }
        boolean notify = online != null && online != sender;
        switch (sub) {
            case "ver": {
                if (amount <= 0) {
                    s().send(sender, "gecersiz-miktar");
                    return;
                }
                double before = bm.get(uuid);
                double after = bm.add(uuid, nameForFile, amount);
                String given = Num.format(after - before);
                s().send(sender, "para-verildi", "{oyuncu}", name, "{miktar}", given, "{bakiye}", Num.format(after));
                if (notify) s().send(online, "para-sana-verildi", "{miktar}", given, "{bakiye}", Num.format(after));
                break;
            }
            case "al": {
                if (amount <= 0) {
                    s().send(sender, "gecersiz-miktar");
                    return;
                }
                double before = bm.get(uuid);
                double after = bm.add(uuid, nameForFile, -amount);
                String taken = Num.format(before - after);
                s().send(sender, "para-alindi", "{oyuncu}", name, "{miktar}", taken, "{bakiye}", Num.format(after));
                if (notify) s().send(online, "para-senden-alindi", "{miktar}", taken, "{bakiye}", Num.format(after));
                break;
            }
            default: { // ayarla
                if (amount < 0) {
                    s().send(sender, "gecersiz-miktar");
                    return;
                }
                double after = bm.set(uuid, nameForFile, amount);
                s().send(sender, "para-ayarlandi", "{oyuncu}", name, "{bakiye}", Num.format(after));
                if (notify) s().send(online, "para-senin-ayarlandi", "{bakiye}", Num.format(after));
            }
        }
        bm.requestSave();
        plugin.market().refreshViewer(uuid);
    }

    // ---------------------------------------------------------------- yardımcılar

    private static final Method OFFLINE_IF_CACHED = resolveOfflineIfCached();

    private static Method resolveOfflineIfCached() {
        try {
            return Bukkit.getServer().getClass().getMethod("getOfflinePlayerIfCached", String.class);
        } catch (Throwable t) {
            return null; // Spigot: yok
        }
    }

    /** Paper'ın kullanıcı önbelleğinden oyuncu; Mojang'a istek atmaz, sunucuyu dondurmaz. */
    private static OfflinePlayer cachedOffline(String name) {
        if (OFFLINE_IF_CACHED == null) return null;
        try {
            Object o = OFFLINE_IF_CACHED.invoke(Bukkit.getServer(), name);
            if (!(o instanceof OfflinePlayer)) return null;
            OfflinePlayer op = (OfflinePlayer) o;
            return op.hasPlayedBefore() || op.isOnline() ? op : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private Double positive(CommandSender sender, String raw) {
        Double d = Num.parse(raw);
        if (d == null) {
            s().send(sender, "gecersiz-sayi", "{deger}", raw);
            return null;
        }
        if (d <= 0 || Num.money(d) <= 0) {
            s().send(sender, "gecersiz-miktar");
            return null;
        }
        return d;
    }

    private void fileError(CommandSender sender, String file, String error) {
        s().send(sender, "dosya-hatasi", "{dosya}", file, "{hata}", error == null ? "yazma hatası (konsola bakın)" : error);
    }

    // ======================================================================= tab

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 0) return Collections.emptyList();
        boolean use = sender.hasPermission(P_USE);
        boolean alan = sender.hasPermission(P_ALAN);
        boolean market = sender.hasPermission(P_MARKET);
        boolean para = sender.hasPermission(P_PARA);
        boolean reload = sender.hasPermission(P_RELOAD);
        String a0 = low(args[0]);

        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            if (use) subs.addAll(Arrays.asList("bakiye", "market", "top"));
            if (alan) subs.add("alan");
            if (para) subs.add("para");
            if (reload) subs.add("reload");
            return filter(subs, args[0]);
        }
        String a1 = low(args[1]);
        if (args.length == 2) {
            if (a0.equals("alan") && alan) return filter(Arrays.asList("oluştur", "sil", "liste", "ayarla"), args[1]);
            if (a0.equals("market") && market) return filter(Arrays.asList("ekle", "sil", "fiyat"), args[1]);
            if (a0.equals("para") && para) return filter(Arrays.asList("ver", "al", "ayarla", "bak"), args[1]);
            return Collections.emptyList();
        }
        if (args.length == 3) {
            if (a0.equals("alan") && alan) {
                if (a1.equals("sil") || a1.equals("ayarla")) return filter(plugin.zones().names(), args[2]);
                if (a1.equals("oluştur") || a1.equals("olustur")) return Collections.singletonList("<isim>");
            }
            if (a0.equals("market") && market) {
                // sadece dolu (satışta olan) slotlar
                if (a1.equals("sil") || a1.equals("fiyat")) return filter(plugin.market().filledSlots(), args[2]);
                if (a1.equals("ekle")) return Collections.singletonList("<fiyat>");
            }
            if (a0.equals("para") && para) return filter(onlineNames(), args[2]);
            return Collections.emptyList();
        }
        if (args.length == 4) {
            if (a0.equals("alan") && alan && a1.equals("ayarla")) return filter(Arrays.asList("cooldown", "odul", "vip-odul"), args[3]);
            if (a0.equals("market") && market) {
                if (a1.equals("ekle")) return filter(plugin.market().emptySlots(), args[3]);
                if (a1.equals("fiyat")) return Collections.singletonList("<fiyat>");
            }
            if (a0.equals("para") && para && !a1.equals("bak")) return Collections.singletonList("<miktar>");
            return Collections.emptyList();
        }
        if (args.length == 5 && a0.equals("alan") && alan && a1.equals("ayarla")) return Collections.singletonList("<değer>");
        return Collections.emptyList();
    }

    private static List<String> onlineNames() {
        List<String> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        return out;
    }

    private static List<String> filter(List<String> options, String typed) {
        String t = low(typed);
        List<String> out = new ArrayList<>(options.size());
        for (String o : options) if (low(o).startsWith(t)) out.add(o);
        return out;
    }
}
