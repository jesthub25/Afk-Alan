package com.jestron.afkalan.balance;

import com.jestron.afkalan.AfkAlan;
import com.jestron.afkalan.util.Num;
import com.jestron.afkalan.util.YamlIO;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * AFK parası bakiyeleri (bakiyeler.yml).
 *
 * Tasarım:
 *  - Bakiyeler bellekte tutulur, okuma O(1) ve thread-safe (PlaceholderAPI async çağırabilir).
 *  - Her değişiklik "bekleyen fark" (delta) olarak da kaydedilir.
 *  - Kayıt, ayrı bir IO thread'inde yapılır: dosya okunur, bekleyen farklar üzerine uygulanır, atomik yazılır.
 *    Böylece dosya elle düzenlenmiş olsa bile (reload atılmasa dahi) düzenleme ezilmez, kazanılan para da kaybolmaz.
 *  - /afk reload: bellek = dosya + henüz kaydedilmemiş farklar.
 *  - Sunucu ana thread'i hiçbir zaman dosya yazmayı beklemez (kapanış hariç).
 */
public final class BalanceManager {

    private static final String HEADER =
            "AfkAlan - oyuncu AFK bakiyeleri\n"
                    + "Elle düzenleyebilirsiniz. Değişiklik bir sonraki kayıtta otomatik algılanır;\n"
                    + "hemen uygulamak için oyunda /afk reload yazın.";

    private final AfkAlan plugin;
    private final File file;

    private final ConcurrentHashMap<UUID, Double> balances = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> names = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UUID> nameIndex = new ConcurrentHashMap<>();

    // Sadece ana thread
    private Map<UUID, Double> pendingDelta = new HashMap<>();
    private Map<UUID, String> pendingNames = new HashMap<>();
    private boolean saveInFlight;
    /** reload her çağrıldığında artar; reload'dan önce başlamış kaydın sonucu belleğe uygulanmaz. */
    private int generation;
    private BukkitTask autoSaveTask;
    private BukkitTask debounceTask;

    // IO thread'inde başarısız olan kayıtların farkları (kaybolmasın diye)
    private final ConcurrentHashMap<UUID, Double> failedDelta = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> failedNames = new ConcurrentHashMap<>();

    private final ExecutorService io;
    private volatile String lastIoError;

    private volatile List<TopEntry> topCache = Collections.emptyList();
    private volatile long topBuiltAt;

    public static final class TopEntry {
        public final String name;
        public final double balance;

        TopEntry(String name, double balance) {
            this.name = name;
            this.balance = balance;
        }
    }

    private static final class Rec {
        String name;
        double bal;
    }

    public BalanceManager(AfkAlan plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "bakiyeler.yml");
        this.io = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "AfkAlan-IO");
            t.setDaemon(true);
            return t;
        });
    }

    // ---------------------------------------------------------------- yükleme

    /** Açılışta (ana thread). @return null başarılı, aksi halde hata. */
    public String loadInitial() {
        try {
            Map<UUID, Rec> data = readFile();
            applyFileState(data);
            if (!file.exists()) writeFile(data);
            return null;
        } catch (Exception e) {
            String err = YamlIO.error(e);
            plugin.getLogger().severe("bakiyeler.yml okunamadı: " + err
                    + " — dosyaya DOKUNULMAYACAK. Düzeltip /afk reload yazın.");
            return err;
        }
    }

    /** /afk reload (ana thread). Devam eden kayıt varsa onun bitmesini bekler. */
    public String reload() {
        drainFailed();
        generation++;
        Future<Map<UUID, Rec>> f;
        try {
            f = io.submit((Callable<Map<UUID, Rec>>) this::readFile);
        } catch (RejectedExecutionException e) {
            return "IO kapalı";
        }
        try {
            applyFileState(f.get(10, TimeUnit.SECONDS));
            return null;
        } catch (ExecutionException e) {
            return YamlIO.error(e.getCause() == null ? e : e.getCause());
        } catch (TimeoutException e) {
            return "zaman aşımı";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "kesildi";
        }
    }

    // ---------------------------------------------------------------- okuma

    public double get(UUID uuid) {
        Double d = balances.get(uuid);
        return d == null ? 0D : d;
    }

    public String name(UUID uuid) {
        return names.get(uuid);
    }

    /** İsimden UUID (bu eklentinin gördüğü oyuncular). Büyük/küçük harf duyarsız. */
    public UUID findByName(String name) {
        return name == null ? null : nameIndex.get(name.toLowerCase(Locale.ROOT));
    }

    public List<String> knownNames() {
        return new ArrayList<>(names.values());
    }

    // ---------------------------------------------------------------- yazma (ana thread)

    /** Bakiye ekler (negatif miktar düşer, 0'ın altına inmez). @return yeni bakiye */
    public double add(UUID uuid, String name, double amount) {
        double cur = get(uuid);
        double next = Num.money(cur + amount);
        double delta = Num.round(next - cur);
        if (delta != 0D || !balances.containsKey(uuid)) {
            balances.put(uuid, next);
            pendingDelta.merge(uuid, delta, Double::sum);
            topBuiltAt = 0L;
        }
        if (name != null) updateName(uuid, name, true);
        return next;
    }

    /** Yeterli bakiye varsa düşer. */
    public boolean take(UUID uuid, double amount) {
        if (amount < 0D) return false;
        if (!Num.gte(get(uuid), amount)) return false;
        add(uuid, null, -amount);
        return true;
    }

    public double set(UUID uuid, String name, double value) {
        return add(uuid, name, Num.money(value) - get(uuid));
    }

    /** Oyuncu girişinde çağrılır. Dosyada kaydı olan oyuncunun ismi değiştiyse kaydedilir. */
    public void onJoin(UUID uuid, String name) {
        updateName(uuid, name, balances.containsKey(uuid));
    }

    private void updateName(UUID uuid, String name, boolean persist) {
        String old = names.put(uuid, name);
        if (old != null && !old.equalsIgnoreCase(name)) nameIndex.remove(old.toLowerCase(Locale.ROOT), uuid);
        nameIndex.put(name.toLowerCase(Locale.ROOT), uuid);
        if (persist && !name.equals(old)) pendingNames.put(uuid, name);
    }

    // ---------------------------------------------------------------- top

    public List<TopEntry> top(int n) {
        long now = System.currentTimeMillis();
        List<TopEntry> cache = topCache;
        if (now - topBuiltAt > 30_000L) {
            List<Map.Entry<UUID, Double>> list = new ArrayList<>(balances.entrySet());
            list.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
            List<TopEntry> out = new ArrayList<>(Math.min(100, list.size()));
            for (Map.Entry<UUID, Double> e : list) {
                if (out.size() >= 100) break;
                if (e.getValue() <= 0D) break;
                String nm = names.get(e.getKey());
                out.add(new TopEntry(nm == null ? e.getKey().toString().substring(0, 8) : nm, e.getValue()));
            }
            cache = Collections.unmodifiableList(out);
            topCache = cache;
            topBuiltAt = now;
        }
        return cache.size() <= n ? cache : cache.subList(0, n);
    }

    // ---------------------------------------------------------------- kayıt

    public void startAutoSave(int minutes) {
        if (autoSaveTask != null) autoSaveTask.cancel();
        long period = Math.max(1, minutes) * 60L * 20L;
        autoSaveTask = Bukkit.getScheduler().runTaskTimer(plugin, this::saveAsync, period, period);
    }

    /** Satın alma / admin işlemlerinden sonra: 3 sn içinde kaydet (art arda işlemler tek kayda toplanır). */
    public void requestSave() {
        if (debounceTask != null || !plugin.isEnabled()) return;
        debounceTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            debounceTask = null;
            saveAsync();
        }, 60L);
    }

    /** Ana thread: bekleyen farkları alır, IO thread'inde dosyaya işler. */
    public void saveAsync() {
        drainFailed();
        if (saveInFlight) {
            requestSave(); // önceki bitince tekrar dene
            return;
        }
        if (pendingDelta.isEmpty() && pendingNames.isEmpty()) return;

        final Map<UUID, Double> deltas = pendingDelta;
        final Map<UUID, String> nms = pendingNames;
        pendingDelta = new HashMap<>();
        pendingNames = new HashMap<>();
        saveInFlight = true;
        final int gen = generation;
        try {
            io.execute(() -> {
                Map<UUID, Rec> result = null;
                try {
                    Map<UUID, Rec> data = readFile();
                    merge(data, deltas, nms);
                    writeFile(data);
                    result = data;
                    if (lastIoError != null) {
                        plugin.getLogger().info("bakiyeler.yml tekrar kaydedilebiliyor.");
                        lastIoError = null;
                    }
                } catch (Throwable t) {
                    for (Map.Entry<UUID, Double> e : deltas.entrySet()) failedDelta.merge(e.getKey(), e.getValue(), Double::sum);
                    for (Map.Entry<UUID, String> e : nms.entrySet()) failedNames.putIfAbsent(e.getKey(), e.getValue());
                    String err = YamlIO.error(t);
                    if (!err.equals(lastIoError)) {
                        plugin.getLogger().severe("bakiyeler.yml kaydedilemedi (veriler bellekte güvende, tekrar denenecek): " + err);
                        lastIoError = err;
                    }
                }
                final Map<UUID, Rec> applied = result;
                if (plugin.isEnabled()) {
                    try {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            saveInFlight = false;
                            drainFailed();
                            if (applied != null && gen == generation) applyFileState(applied);
                        });
                    } catch (Throwable ignored) {
                        // eklenti kapanıyor; shutdown() kalanları kaydeder
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            saveInFlight = false;
            for (Map.Entry<UUID, Double> en : deltas.entrySet()) pendingDelta.merge(en.getKey(), en.getValue(), Double::sum);
            for (Map.Entry<UUID, String> en : nms.entrySet()) pendingNames.putIfAbsent(en.getKey(), en.getValue());
        }
    }

    /** onDisable: kalan her şeyi senkron kaydeder. Dosya bozuksa ona dokunmaz, kurtarma dosyası yazar. */
    public void shutdown() {
        if (autoSaveTask != null) autoSaveTask.cancel();
        if (debounceTask != null) debounceTask.cancel();
        autoSaveTask = null;
        debounceTask = null;
        io.shutdown();
        try {
            if (!io.awaitTermination(15, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Bakiye kaydı 15 saniyede bitmedi.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        drainFailed();
        if (pendingDelta.isEmpty() && pendingNames.isEmpty()) return;
        try {
            Map<UUID, Rec> data = readFile();
            merge(data, pendingDelta, pendingNames);
            writeFile(data);
            pendingDelta.clear();
            pendingNames.clear();
        } catch (Throwable t) {
            writeRescue(t);
        }
    }

    private void writeRescue(Throwable cause) {
        String ts = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());
        File rescue = new File(plugin.getDataFolder(), "bakiyeler-kurtarma-" + ts + ".yml");
        Map<UUID, Rec> data = new HashMap<>();
        for (Map.Entry<UUID, Double> e : balances.entrySet()) {
            Rec r = new Rec();
            r.bal = e.getValue();
            r.name = names.get(e.getKey());
            data.put(e.getKey(), r);
        }
        try {
            YamlIO.write(rescue, "KURTARMA DOSYASI - bakiyeler.yml bozuk olduğu için bellekteki bakiyeler buraya yazıldı.\n"
                    + "Sebep: " + YamlIO.error(cause), toYaml(data));
            plugin.getLogger().severe("bakiyeler.yml bozuk! Bellekteki bakiyeler " + rescue.getName() + " dosyasına yazıldı.");
        } catch (Exception e) {
            plugin.getLogger().severe("Kurtarma dosyası da yazılamadı: " + YamlIO.error(e));
        }
    }

    // ---------------------------------------------------------------- iç

    private void drainFailed() {
        if (!failedDelta.isEmpty()) {
            for (UUID u : new ArrayList<>(failedDelta.keySet())) {
                Double d = failedDelta.remove(u);
                if (d != null) pendingDelta.merge(u, d, Double::sum);
            }
        }
        if (!failedNames.isEmpty()) {
            for (UUID u : new ArrayList<>(failedNames.keySet())) {
                String n = failedNames.remove(u);
                if (n != null) pendingNames.putIfAbsent(u, n);
            }
        }
    }

    /** Ana thread: bellek = dosya + kaydedilmemiş farklar. */
    private void applyFileState(Map<UUID, Rec> data) {
        for (UUID u : new ArrayList<>(balances.keySet())) {
            if (!data.containsKey(u) && !pendingDelta.containsKey(u)) balances.remove(u);
        }
        for (Map.Entry<UUID, Rec> e : data.entrySet()) {
            UUID u = e.getKey();
            Double d = pendingDelta.get(u);
            balances.put(u, Num.money(e.getValue().bal + (d == null ? 0D : d)));
            String nm = e.getValue().name;
            if (nm != null && !nm.isEmpty() && !pendingNames.containsKey(u)) updateName(u, nm, false);
        }
        for (Map.Entry<UUID, Double> e : pendingDelta.entrySet()) {
            if (!data.containsKey(e.getKey())) balances.put(e.getKey(), Num.money(e.getValue()));
        }
        topBuiltAt = 0L;
    }

    private static void merge(Map<UUID, Rec> data, Map<UUID, Double> deltas, Map<UUID, String> nms) {
        for (Map.Entry<UUID, Double> e : deltas.entrySet()) {
            Rec r = data.get(e.getKey());
            if (r == null) data.put(e.getKey(), r = new Rec());
            r.bal = Num.money(r.bal + e.getValue());
        }
        for (Map.Entry<UUID, String> e : nms.entrySet()) {
            Rec r = data.get(e.getKey());
            if (r == null) data.put(e.getKey(), r = new Rec());
            r.name = e.getValue();
        }
    }

    private Map<UUID, Rec> readFile() throws Exception {
        YamlConfiguration y = YamlIO.read(file);
        Map<UUID, Rec> out = new HashMap<>();
        ConfigurationSection s = y.getConfigurationSection("oyuncular");
        if (s == null) return out;
        for (String key : s.getKeys(false)) {
            UUID u;
            try {
                u = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            Rec r = new Rec();
            if (s.isConfigurationSection(key)) {
                r.name = s.getString(key + ".isim");
                r.bal = s.getDouble(key + ".bakiye", 0D);
            } else {
                r.bal = s.getDouble(key, 0D);
            }
            r.bal = Num.money(r.bal);
            out.put(u, r);
        }
        return out;
    }

    private void writeFile(Map<UUID, Rec> data) throws Exception {
        YamlIO.write(file, HEADER, toYaml(data));
    }

    private static YamlConfiguration toYaml(Map<UUID, Rec> data) {
        List<Map.Entry<UUID, Rec>> list = new ArrayList<>(data.entrySet());
        list.sort((a, b) -> Double.compare(b.getValue().bal, a.getValue().bal));
        YamlConfiguration y = new YamlConfiguration();
        y.createSection("oyuncular");
        for (Map.Entry<UUID, Rec> e : list) {
            String p = "oyuncular." + e.getKey() + ".";
            if (e.getValue().name != null) y.set(p + "isim", e.getValue().name);
            y.set(p + "bakiye", e.getValue().bal);
        }
        return y;
    }
}
