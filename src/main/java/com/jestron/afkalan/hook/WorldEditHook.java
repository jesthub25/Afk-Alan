package com.jestron.afkalan.hook;

import com.jestron.afkalan.AfkAlan;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * WorldEdit / FastAsyncWorldEdit seçimini (pos1-pos2) yansıma ile okur.
 * Derleme bağımlılığı yoktur; WE 7.2 (1.16-1.19) ve 7.3+ (1.20-1.21) ile, FAWE ile çalışır.
 * Metotlar bir kez çözülür ve önbelleğe alınır.
 */
public final class WorldEditHook {

    public enum Status { OK, NOT_INSTALLED, NO_SELECTION }

    public static final class Selection {
        public final Status status;
        public final int minX, minY, minZ, maxX, maxY, maxZ;

        Selection(Status status, int[] a) {
            this.status = status;
            this.minX = a[0];
            this.minY = a[1];
            this.minZ = a[2];
            this.maxX = a[3];
            this.maxY = a[4];
            this.maxZ = a[5];
        }
    }

    private static final int[] ZERO = new int[6];

    private final AfkAlan plugin;
    private boolean ready;
    private String pluginName;

    private Object worldEdit;
    private Method getSessionManager, sessionGet, getSelection, getMin, getMax;
    private Method adaptPlayer, adaptWorld;
    private Method vx, vy, vz;

    public WorldEditHook(AfkAlan plugin) {
        this.plugin = plugin;
    }

    /** @return bağlantı kuruldu mu */
    public boolean init() {
        ready = false;
        Plugin we = Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit");
        if (we == null || !we.isEnabled()) we = Bukkit.getPluginManager().getPlugin("WorldEdit");
        if (we == null || !we.isEnabled()) return false;
        try {
            ClassLoader cl = we.getClass().getClassLoader();
            Class<?> weClass = Class.forName("com.sk89q.worldedit.WorldEdit", true, cl);
            Class<?> adapter = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter", true, cl);
            Class<?> region = Class.forName("com.sk89q.worldedit.regions.Region", true, cl);

            worldEdit = weClass.getMethod("getInstance").invoke(null);
            getSessionManager = weClass.getMethod("getSessionManager");
            adaptPlayer = staticMethod(adapter, "adapt", Player.class);
            adaptWorld = staticMethod(adapter, "adapt", World.class);

            // Çalışma zamanı sınıfı yerine bildirilen tip: public olmayan alt sınıflarda erişim hatası olmasın
            sessionGet = oneArg(getSessionManager.getReturnType(), "get", adaptPlayer.getReturnType());
            getSelection = oneArg(sessionGet.getReturnType(), "getSelection", adaptWorld.getReturnType());
            getMin = region.getMethod("getMinimumPoint");
            getMax = region.getMethod("getMaximumPoint");

            Class<?> vec = getMin.getReturnType();
            vx = intGetter(vec, "x", "getX", "getBlockX");
            vy = intGetter(vec, "y", "getY", "getBlockY");
            vz = intGetter(vec, "z", "getZ", "getBlockZ");

            pluginName = we.getName() + " " + we.getDescription().getVersion();
            ready = true;
            plugin.getLogger().info(pluginName + " bağlantısı hazır.");
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning(we.getName() + " bulundu ama bağlanılamadı: " + t);
            return false;
        }
    }

    public boolean ready() {
        return ready;
    }

    public String pluginName() {
        return pluginName;
    }

    public Selection selection(Player player) {
        if (!ready && !init()) return new Selection(Status.NOT_INSTALLED, ZERO);
        try {
            Object actor = adaptPlayer.invoke(null, player);
            Object session = sessionGet.invoke(getSessionManager.invoke(worldEdit), actor);
            if (session == null) return new Selection(Status.NO_SELECTION, ZERO);
            Object weWorld = adaptWorld.invoke(null, player.getWorld());
            Object region = getSelection.invoke(session, weWorld);
            if (region == null) return new Selection(Status.NO_SELECTION, ZERO);
            Object min = getMin.invoke(region);
            Object max = getMax.invoke(region);
            int[] a = {
                    ((Number) vx.invoke(min)).intValue(), ((Number) vy.invoke(min)).intValue(), ((Number) vz.invoke(min)).intValue(),
                    ((Number) vx.invoke(max)).intValue(), ((Number) vy.invoke(max)).intValue(), ((Number) vz.invoke(max)).intValue()};
            return new Selection(Status.OK, a);
        } catch (InvocationTargetException e) {
            // IncompleteRegionException: pos1/pos2 seçilmemiş veya başka dünyada seçilmiş
            return new Selection(Status.NO_SELECTION, ZERO);
        } catch (Throwable t) {
            plugin.getLogger().warning("WorldEdit seçimi okunamadı: " + t);
            return new Selection(Status.NO_SELECTION, ZERO);
        }
    }

    // ---------------------------------------------------------------- yansıma yardımcıları

    private static Method staticMethod(Class<?> owner, String name, Class<?> arg) throws NoSuchMethodException {
        for (Method m : owner.getMethods()) {
            if (!m.getName().equals(name) || !Modifier.isStatic(m.getModifiers())) continue;
            Class<?>[] p = m.getParameterTypes();
            if (p.length == 1 && p[0] == arg) return m;
        }
        for (Method m : owner.getMethods()) {
            if (!m.getName().equals(name) || !Modifier.isStatic(m.getModifiers())) continue;
            Class<?>[] p = m.getParameterTypes();
            if (p.length == 1 && p[0].isAssignableFrom(arg)) return m;
        }
        throw new NoSuchMethodException(owner.getName() + "." + name + "(" + arg.getName() + ")");
    }

    /** Parametresi arg tipini kabul eden tek parametreli public metot (ör. SessionManager.get(SessionOwner)). */
    private static Method oneArg(Class<?> owner, String name, Class<?> arg) throws NoSuchMethodException {
        Method best = null;
        for (Method m : owner.getMethods()) {
            if (!m.getName().equals(name) || Modifier.isStatic(m.getModifiers())) continue;
            Class<?>[] p = m.getParameterTypes();
            if (p.length != 1 || !p[0].isAssignableFrom(arg)) continue;
            // en spesifik parametre tipini seç
            if (best == null || best.getParameterTypes()[0].isAssignableFrom(p[0])) best = m;
        }
        if (best == null) throw new NoSuchMethodException(owner.getName() + "." + name + "(" + arg.getName() + ")");
        return best;
    }

    private static Method intGetter(Class<?> owner, String... names) throws NoSuchMethodException {
        for (String n : names) {
            try {
                Method m = owner.getMethod(n);
                Class<?> r = m.getReturnType();
                if (r == int.class || r == double.class || r == Integer.class || r == Double.class) return m;
            } catch (NoSuchMethodException ignored) {
                // sıradakini dene
            }
        }
        throw new NoSuchMethodException(owner.getName() + " koordinat metodu");
    }
}
