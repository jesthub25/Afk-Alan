package com.jestron.afkalan.afk;

import com.jestron.afkalan.AfkAlan;
import com.jestron.afkalan.Settings;
import com.jestron.afkalan.api.AfkOdulEvent;
import com.jestron.afkalan.display.AfkBoard;
import com.jestron.afkalan.hook.PapiBridge;
import com.jestron.afkalan.util.Num;
import com.jestron.afkalan.util.Text;
import com.jestron.afkalan.zone.AfkZone;
import com.jestron.afkalan.zone.ZoneManager;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Saniyede bir çalışan tek görev. Hareket eventi dinlenmez (PlayerMoveEvent saniyede yüzlerce kez tetiklenir).
 *
 * Akış (oyuncu başına):
 *   alan dışı + durum yok  -> dünya indeksine bakış (dünyada alan yoksa O(1)), başka hiçbir şey yok
 *   giriş                  -> durum oluştur, sayaç = cooldown, AFK scoreboard'u tak
 *   içeride                -> sayaç--, 0 olunca ödül + sayaç baştan
 *   çıkış                  -> sayaç silinir, title temizlenir, önceki scoreboard geri verilir
 */
public final class AfkTracker implements Runnable {

    private final AfkAlan plugin;
    private final Map<UUID, AfkState> states = new ConcurrentHashMap<>();
    private final Location tmp = new Location(null, 0, 0, 0);
    private BukkitTask task;

    public AfkTracker(AfkAlan plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (task == null) task = Bukkit.getScheduler().runTaskTimer(plugin, this, 20L, 20L);
    }

    /** onDisable: herkesin title/scoreboard'unu eski haline getirir. */
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Map.Entry<UUID, AfkState> e : new ArrayList<>(states.entrySet())) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) clearVisuals(p, e.getValue());
        }
        states.clear();
    }

    public AfkState state(UUID uuid) {
        return states.get(uuid);
    }

    public int activeCount() {
        return states.size();
    }

    @Override
    public void run() {
        ZoneManager zm = plugin.zones();
        Settings s = plugin.settings();
        try {
            for (Player p : Bukkit.getOnlinePlayers()) {
                UUID uuid = p.getUniqueId();
                AfkState st = states.get(uuid);
                AfkZone zone = locate(p, st, zm, s);

                if (zone == null) {
                    if (st != null) exit(p, st);
                    continue;
                }
                if (st == null) {
                    enter(p, zone, s);
                    continue;
                }
                if (st.zone != zone) {
                    boolean other = !st.zone.getName().equalsIgnoreCase(zone.getName());
                    st.zone = zone;
                    if (other) {
                        // Başka bir alana geçti: sayaç o alanın süresiyle baştan
                        st.remaining = zone.getCooldown();
                        st.nextReward = rewardFor(p, zone);
                        show(p, st, s);
                        continue;
                    }
                }
                tick(p, st, s);
            }
        } finally {
            tmp.setWorld(null); // dünya referansını tutma
        }
    }

    private AfkZone locate(Player p, AfkState st, ZoneManager zm, Settings s) {
        if (p.isDead()) return null;
        if (!s.ignoredModes.isEmpty() && s.ignoredModes.contains(p.getGameMode())) return null;
        p.getLocation(tmp); // yeni Location nesnesi oluşturmaz
        World w = tmp.getWorld();
        if (w == null) return null;
        String world = w.getName();
        double x = tmp.getX(), y = tmp.getY(), z = tmp.getZ();
        if (st != null) {
            AfkZone cur = st.zone;
            // AFK oyuncu çoğunlukla aynı yerde durur: önce mevcut alan (tek AABB kontrolü)
            if (cur.getWorld().equals(world) && cur.contains(x, y, z)) return cur;
        }
        return zm.find(world, x, y, z);
    }

    private void enter(Player p, AfkZone zone, Settings s) {
        AfkState st = new AfkState(zone);
        st.nextReward = rewardFor(p, zone);
        states.put(p.getUniqueId(), st);
        show(p, st, s);
    }

    private void exit(Player p, AfkState st) {
        states.remove(p.getUniqueId());
        clearVisuals(p, st);
    }

    private void tick(Player p, AfkState st, Settings s) {
        int rem = st.remaining - 1;
        if (rem <= 0) {
            reward(p, st, s);
            rem = st.zone.getCooldown();
        }
        st.remaining = rem;
        show(p, st, s);
    }

    private double rewardFor(Player p, AfkZone z) {
        String perm = z.getVipPermission();
        boolean vip = !perm.isEmpty() && p.hasPermission(perm);
        return vip ? z.getVipReward() : z.getReward();
    }

    private void reward(Player p, AfkState st, Settings s) {
        AfkZone z = st.zone;
        double amount = rewardFor(p, z);
        st.nextReward = amount;
        if (AfkOdulEvent.getHandlerList().getRegisteredListeners().length > 0) {
            AfkOdulEvent ev = new AfkOdulEvent(p, z.getName(), amount);
            Bukkit.getPluginManager().callEvent(ev);
            if (ev.isCancelled()) return;
            amount = ev.getAmount();
        }
        amount = Num.money(amount);
        if (amount <= 0D) return;
        double bal = plugin.balances().add(p.getUniqueId(), p.getName(), amount);
        s.send(p, "odul-verildi",
                "{miktar}", Num.format(amount),
                "{bakiye}", Num.format(bal),
                "{alan}", z.getName());
        plugin.playSound(p, s.soundReward);
        plugin.market().refreshViewer(p.getUniqueId());
    }

    // ---------------------------------------------------------------- görüntü

    private void show(Player p, AfkState st, Settings s) {
        Settings.CounterMode mode = s.counterMode;
        if (mode != Settings.CounterMode.KAPALI) {
            AfkZone z = st.zone;
            int cd = z.getCooldown();
            int rem = st.remaining;
            String color = rem > cd * 0.5D ? s.colorHigh : (rem > cd * 0.2D ? s.colorMid : s.colorLow);
            String remText = String.valueOf(rem);
            String odul = Num.format(st.nextReward);
            boolean title = mode == Settings.CounterMode.TITLE || mode == Settings.CounterMode.IKISI;
            boolean bar = mode == Settings.CounterMode.ACTIONBAR || mode == Settings.CounterMode.IKISI;
            String barText = (title && (s.titleNeedsBar || s.subtitleNeedsBar)) || (bar && s.actionbarNeedsBar)
                    ? bar(rem, cd, s) : "";
            if (title) {
                p.sendTitle(
                        fill(s.title, z, remText, cd, color, barText, odul),
                        fill(s.subtitle, z, remText, cd, color, barText, odul),
                        0, s.titleStay, 0);
            }
            if (bar) {
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText(fill(s.actionbar, z, remText, cd, color, barText, odul)));
            }
        }
        if (s.boardEnabled) updateBoard(p, st, s);
    }

    private static String fill(String tpl, AfkZone z, String rem, int cd, String color, String bar, String odul) {
        return Text.replace(tpl,
                "{renk}", color,
                "{kalan}", rem,
                "{alan}", z.getName(),
                "{cooldown}", String.valueOf(cd),
                "{odul}", odul,
                "{bar}", bar);
    }

    private static String bar(int rem, int total, Settings s) {
        int len = s.barLength;
        int filled = total > 0 ? (int) Math.round((double) rem / total * len) : 0;
        if (filled < 0) filled = 0;
        if (filled > len) filled = len;
        StringBuilder b = new StringBuilder(len * (s.barFull.length() + 1));
        for (int i = 0; i < len; i++) b.append(i < filled ? s.barFull : s.barEmpty);
        return b.toString();
    }

    private void updateBoard(Player p, AfkState st, Settings s) {
        AfkBoard b = st.board;
        if (b == null) {
            b = AfkBoard.build(p.getScoreboard(), s.boardTitle, s.boardLines.length, s.boardHideNumbers);
            if (b == null) return;
            st.board = b;
            p.setScoreboard(b.scoreboard());
        }
        String[] lines = s.boardLines;
        String bal = null, rem = null, odul = null;
        for (int i = 0; i < lines.length && i < b.size(); i++) {
            String line = lines[i];
            if (!s.boardLineStatic[i]) {
                if (bal == null) {
                    bal = Num.format(plugin.balances().get(p.getUniqueId()));
                    rem = String.valueOf(st.remaining);
                    odul = Num.format(st.nextReward);
                }
                line = Text.replace(line,
                        "{bakiye}", bal,
                        "{para}", s.currency,
                        "{alan}", st.zone.getName(),
                        "{kalan}", rem,
                        "{cooldown}", String.valueOf(st.zone.getCooldown()),
                        "{odul}", odul,
                        "{oyuncu}", p.getName());
                if (s.boardLinePapi[i] && plugin.papiEnabled()) line = PapiBridge.apply(p, line);
            }
            b.line(i, line);
        }
    }

    private void clearVisuals(Player p, AfkState st) {
        Settings s = plugin.settings();
        if (s.counterMode == Settings.CounterMode.TITLE || s.counterMode == Settings.CounterMode.IKISI) {
            p.resetTitle();
        }
        if (s.counterMode == Settings.CounterMode.ACTIONBAR || s.counterMode == Settings.CounterMode.IKISI) {
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
        }
        restoreBoard(p, st);
    }

    private static void restoreBoard(Player p, AfkState st) {
        AfkBoard b = st.board;
        st.board = null;
        if (b == null) return;
        // Başka bir eklenti bu arada scoreboard'u değiştirdiyse onunkine dokunma
        if (p.isOnline() && p.getScoreboard() == b.scoreboard()) {
            Scoreboard prev = b.previous();
            if (prev == null && Bukkit.getScoreboardManager() != null) prev = Bukkit.getScoreboardManager().getMainScoreboard();
            if (prev != null) p.setScoreboard(prev);
        }
    }

    // ---------------------------------------------------------------- olaylar

    /** Oyuncu çıktı: durum silinir; tekrar girince sayaç baştan başlar. */
    public void onQuit(Player p) {
        AfkState st = states.remove(p.getUniqueId());
        if (st != null) restoreBoard(p, st);
    }

    /**
     * Alan/config değiştikten sonra: alandaki oyuncuların alanını yeni haliyle eşler.
     * Silinen alandakiler çıkarılır; cooldown kısaldıysa sayaç kısaltılır.
     * @param rebuildBoards config yenilendiyse scoreboard'lar yeni satırlarla kurulur
     */
    public void resync(boolean rebuildBoards) {
        Settings s = plugin.settings();
        for (Map.Entry<UUID, AfkState> e : new ArrayList<>(states.entrySet())) {
            AfkState st = e.getValue();
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) {
                states.remove(e.getKey());
                continue;
            }
            AfkZone z = plugin.zones().get(st.zone.getName());
            if (z == null) {
                exit(p, st);
                continue;
            }
            st.zone = z;
            if (st.remaining > z.getCooldown()) st.remaining = z.getCooldown();
            st.nextReward = rewardFor(p, z);
            if (rebuildBoards) {
                p.resetTitle();
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
                AfkBoard old = st.board;
                st.board = null;
                if (old != null) {
                    boolean ours = p.getScoreboard() == old.scoreboard();
                    Scoreboard prev = old.previous();
                    if (s.boardEnabled) {
                        AfkBoard nb = AfkBoard.build(prev, s.boardTitle, s.boardLines.length, s.boardHideNumbers);
                        st.board = nb;
                        if (ours && nb != null) p.setScoreboard(nb.scoreboard());
                    } else if (ours) {
                        if (prev == null && Bukkit.getScoreboardManager() != null) prev = Bukkit.getScoreboardManager().getMainScoreboard();
                        if (prev != null) p.setScoreboard(prev);
                    }
                }
            }
            show(p, st, s);
        }
    }
}
