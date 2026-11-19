package com.jestron.afkalan.util;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * YAML okuma/yazma.
 * - Okuma: sözdizimi hatası sessizce boş dosya gibi davranmaz, istisna fırlatır (veri kaybını önler).
 * - Yazma: önce .tmp dosyasına yazılır, sonra atomik olarak yer değiştirilir (yarım dosya oluşmaz).
 */
public final class YamlIO {

    private YamlIO() {
    }

    public static YamlConfiguration read(File file) throws IOException, InvalidConfigurationException {
        YamlConfiguration y = new YamlConfiguration();
        if (!file.isFile()) return y;
        String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        if (!content.isEmpty() && content.charAt(0) == '﻿') content = content.substring(1); // BOM
        y.loadFromString(content);
        return y;
    }

    public static void write(File file, String header, YamlConfiguration y) throws IOException {
        StringBuilder sb = new StringBuilder(1024);
        if (header != null && !header.isEmpty()) {
            for (String line : header.split("\n", -1)) {
                sb.append(line.isEmpty() ? "#" : "# " + line).append('\n');
            }
            sb.append('\n');
        }
        sb.append(y.saveToString());
        writeAtomic(file, sb.toString());
    }

    public static void writeAtomic(File file, String data) throws IOException {
        File dir = file.getAbsoluteFile().getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) {
            throw new IOException("Klasör oluşturulamadı: " + dir);
        }
        Path target = file.toPath();
        Path tmp = new File(dir, file.getName() + ".tmp").toPath();
        Files.write(tmp, data.getBytes(StandardCharsets.UTF_8));
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Dosyanın değişip değişmediğini anlamak için (son değişiklik zamanı + boyut). */
    public static long[] stamp(File file) {
        return new long[]{file.lastModified(), file.length()};
    }

    public static boolean changed(File file, long[] stamp) {
        return stamp == null || file.lastModified() != stamp[0] || file.length() != stamp[1];
    }

    public static String error(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c) c = c.getCause();
        String m = c.getMessage();
        if (m == null) return c.getClass().getSimpleName();
        m = m.replace('\n', ' ').replace('\r', ' ');
        return m.length() > 180 ? m.substring(0, 180) + "..." : m;
    }
}
