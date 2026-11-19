package com.jestron.afkalan.util;

import org.bukkit.ChatColor;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Renk ve metin yardımcıları. Durumsuz, her thread'den güvenle çağrılabilir. */
public final class Text {

    private static final Pattern HEX = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final char SECTION = '§';

    private Text() {
    }

    /** &-renk kodlarını ve &#RRGGBB hex renklerini çevirir. */
    public static String color(String s) {
        if (s == null || s.isEmpty()) return "";
        if (s.indexOf("&#") >= 0) {
            Matcher m = HEX.matcher(s);
            StringBuffer sb = new StringBuffer(s.length() + 32);
            while (m.find()) {
                m.appendReplacement(sb, Matcher.quoteReplacement(hex(m.group(1))));
            }
            m.appendTail(sb);
            s = sb.toString();
        }
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private static String hex(String rgb) {
        StringBuilder b = new StringBuilder(14);
        b.append(SECTION).append('x');
        for (int i = 0; i < rgb.length(); i++) {
            b.append(SECTION).append(Character.toLowerCase(rgb.charAt(i)));
        }
        return b.toString();
    }

    /** {anahtar} değer çiftlerini değiştirir: replace(s, "{a}", "1", "{b}", "2"). */
    public static String replace(String s, String... kv) {
        if (s == null || s.isEmpty() || kv.length == 0) return s == null ? "" : s;
        for (int i = 0; i + 1 < kv.length; i += 2) {
            String k = kv[i];
            if (s.contains(k)) s = s.replace(k, kv[i + 1] == null ? "" : kv[i + 1]);
        }
        return s;
    }

    /** Metni renk kodlarını bozmadan en fazla max karaktere kırpar. */
    public static String limit(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        int cut = max;
        // yarım kalan § kodu
        if (s.charAt(cut - 1) == SECTION) cut--;
        // yarım kalan hex dizisi (§x§r§r§g§g§b§b = 14 karakter)
        int from = Math.max(0, cut - 14);
        int x = s.lastIndexOf(SECTION + "x", cut - 1);
        if (x >= from && x + 14 > cut) cut = x;
        return s.substring(0, Math.max(0, cut));
    }

    /** DIAMOND_SWORD -> Diamond Sword */
    public static String prettyMaterial(String name) {
        String[] parts = name.toLowerCase(Locale.ROOT).split("_");
        StringBuilder b = new StringBuilder(name.length());
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (b.length() > 0) b.append(' ');
            b.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return b.toString();
    }
}
