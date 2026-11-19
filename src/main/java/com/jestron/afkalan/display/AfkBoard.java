package com.jestron.afkalan.display;

import com.jestron.afkalan.util.Text;
import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

import java.lang.reflect.Method;

/**
 * Oyuncuya özel, titreşimsiz (flicker-free) AFK scoreboard'u.
 * Her satır sabit bir görünmez giriş + takım öneki (prefix) ile çizilir;
 * güncellemede sadece değişen satırın öneki gönderilir, skor silinip yeniden yazılmaz.
 */
public final class AfkBoard {

    private static final String[] ENTRIES = new String[16];

    static {
        ChatColor[] cs = {ChatColor.BLACK, ChatColor.DARK_BLUE, ChatColor.DARK_GREEN, ChatColor.DARK_AQUA,
                ChatColor.DARK_RED, ChatColor.DARK_PURPLE, ChatColor.GOLD, ChatColor.GRAY, ChatColor.DARK_GRAY,
                ChatColor.BLUE, ChatColor.GREEN, ChatColor.AQUA, ChatColor.RED, ChatColor.LIGHT_PURPLE,
                ChatColor.YELLOW, ChatColor.WHITE};
        for (int i = 0; i < 16; i++) ENTRIES[i] = cs[i].toString() + ChatColor.RESET;
    }

    /** Oyuncunun AFK alanına girmeden önceki scoreboard'u (çıkışta geri verilir). */
    final Scoreboard previous;
    final Scoreboard board;
    private final Objective objective;
    private final Team[] teams;
    private final String[] last;

    private AfkBoard(Scoreboard previous, Scoreboard board, Objective objective, Team[] teams) {
        this.previous = previous;
        this.board = board;
        this.objective = objective;
        this.teams = teams;
        this.last = new String[teams.length];
    }

    /** Oyuncuya bağlı olmadan board kurar (oyuncuya atama çağırana aittir). null = kurulamadı. */
    public static AfkBoard build(Scoreboard previous, String title, int lines, boolean hideNumbers) {
        ScoreboardManager sm = Bukkit.getScoreboardManager();
        if (sm == null || lines <= 0) return null;
        Scoreboard b = sm.getNewScoreboard();
        Objective o = registerObjective(b, "afkalan", title);
        if (o == null) return null;
        o.setDisplaySlot(DisplaySlot.SIDEBAR);
        int n = Math.min(lines, 15);
        Team[] teams = new Team[n];
        for (int i = 0; i < n; i++) {
            Team t = b.registerNewTeam("afkalan_" + i);
            t.addEntry(ENTRIES[i]);
            o.getScore(ENTRIES[i]).setScore(n - i);
            teams[i] = t;
        }
        if (hideNumbers) NumberHider.hide(o);
        return new AfkBoard(previous, b, o, teams);
    }

    public int size() {
        return teams.length;
    }

    /** Sadece değişen satırı günceller. */
    public void line(int i, String text) {
        if (i < 0 || i >= teams.length) return;
        String t = Text.limit(text == null ? "" : text, 64);
        if (t.equals(last[i])) return;
        teams[i].setPrefix(t);
        last[i] = t;
    }

    public void title(String title) {
        if (!title.equals(objective.getDisplayName())) objective.setDisplayName(title);
    }

    public Scoreboard scoreboard() {
        return board;
    }

    /** Oyuncunun AFK alanına girmeden önceki scoreboard'u. */
    public Scoreboard previous() {
        return previous;
    }

    @SuppressWarnings("deprecation")
    private static Objective registerObjective(Scoreboard b, String name, String title) {
        try {
            return b.registerNewObjective(name, "dummy", title);
        } catch (NoSuchMethodError | UnsupportedOperationException e) {
            // Çok yeni sürümlerde eski metot kaldırılırsa Criteria ile dene
            try {
                Class<?> criteria = Class.forName("org.bukkit.scoreboard.Criteria");
                Object dummy = criteria.getField("DUMMY").get(null);
                Method m = Scoreboard.class.getMethod("registerNewObjective", String.class, criteria, String.class);
                return (Objective) m.invoke(b, name, dummy, title);
            } catch (Throwable t) {
                Bukkit.getLogger().warning("[AfkAlan] Scoreboard objective oluşturulamadı: " + t);
                return null;
            }
        }
    }

    /** Paper 1.20.3+ : sağdaki kırmızı skor sayılarını gizler. Desteklenmiyorsa sessizce atlar. */
    private static final class NumberHider {
        private static final Method SET;
        private static final Object BLANK;

        static {
            Method set = null;
            Object blank = null;
            try {
                Class<?> nf = Class.forName("io.papermc.paper.scoreboard.numbers.NumberFormat");
                blank = nf.getMethod("blank").invoke(null);
                set = Objective.class.getMethod("numberFormat", nf);
            } catch (Throwable ignored) {
                set = null;
            }
            SET = set;
            BLANK = blank;
        }

        static void hide(Objective o) {
            if (SET == null) return;
            try {
                SET.invoke(o, BLANK);
            } catch (Throwable ignored) {
                // desteklenmiyor
            }
        }
    }
}
