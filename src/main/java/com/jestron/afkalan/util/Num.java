package com.jestron.afkalan.util;

/** Para hesapları: her değer kuruş (0.01) hassasiyetine yuvarlanır. Thread-safe. */
public final class Num {

    public static final double MAX = 1_000_000_000_000D;
    private static final double EPS = 1e-9;

    private Num() {
    }

    public static double round(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return 0D;
        return Math.round(v * 100D) / 100D;
    }

    /** 0 ile MAX arasına sıkıştırıp yuvarlar. */
    public static double money(double v) {
        if (Double.isNaN(v) || v <= 0D) return 0D;
        if (v > MAX) return MAX;
        return round(v);
    }

    public static boolean gte(double a, double b) {
        return a + EPS >= b;
    }

    /** 12 -> "12", 12.5 -> "12.50", 0.05 -> "0.05" */
    public static String format(double v) {
        long cents = Math.round(round(v) * 100D);
        boolean neg = cents < 0;
        if (neg) cents = -cents;
        long whole = cents / 100;
        int frac = (int) (cents % 100);
        StringBuilder b = new StringBuilder(16);
        if (neg) b.append('-');
        b.append(whole);
        if (frac != 0) {
            b.append('.');
            if (frac < 10) b.append('0');
            b.append(frac);
        }
        return b.toString();
    }

    /** "1,5" ve "1.5" kabul edilir. Geçersizse null. */
    public static Double parse(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            double d = Double.parseDouble(s.trim().replace(',', '.'));
            if (Double.isNaN(d) || Double.isInfinite(d)) return null;
            return d;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Integer parseInt(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return Integer.parseInt(s.trim().replace("#", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
