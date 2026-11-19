package com.jestron.afkalan;

import com.jestron.afkalan.afk.AfkTracker;
import com.jestron.afkalan.balance.BalanceManager;
import com.jestron.afkalan.command.AfkCommand;
import com.jestron.afkalan.hook.PapiExpansion;
import com.jestron.afkalan.hook.WorldEditHook;
import com.jestron.afkalan.listener.PlayerListener;
import com.jestron.afkalan.market.MarketListener;
import com.jestron.afkalan.market.MarketManager;
import com.jestron.afkalan.zone.ZoneManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class AfkAlan extends JavaPlugin {

    private Settings settings;
    private ZoneManager zones;
    private BalanceManager balances;
    private MarketManager market;
    private AfkTracker tracker;
    private WorldEditHook worldEdit;
    private Object papiExpansion; // PlaceholderAPI yoksa sınıfı hiç yüklenmesin diye Object
    private boolean papi;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        papi = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");

        settings = new Settings(this);
        settings.load(papi);

        zones = new ZoneManager(this);
        zones.load();

        balances = new BalanceManager(this);
        balances.loadInitial();
        balances.startAutoSave(settings.autosaveMinutes);

        market = new MarketManager(this);
        market.load();

        worldEdit = new WorldEditHook(this);
        if (!worldEdit.init()) {
            getLogger().info("WorldEdit/FAWE bulunamadı; /afk alan oluştur için gerekli (yüklenince otomatik bağlanır).");
        }

        tracker = new AfkTracker(this);
        tracker.start();

        PluginCommand cmd = getCommand("afk");
        if (cmd == null) {
            getLogger().severe("plugin.yml içinde 'afk' komutu yok! Eklenti kapatılıyor.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        AfkCommand executor = new AfkCommand(this);
        cmd.setExecutor(executor);
        cmd.setTabCompleter(executor);

        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new MarketListener(market), this);

        // /reload sonrası zaten oyunda olan oyuncuların isimleri
        for (Player p : Bukkit.getOnlinePlayers()) balances.onJoin(p.getUniqueId(), p.getName());

        if (papi) {
            try {
                PapiExpansion exp = new PapiExpansion(this);
                if (exp.register()) {
                    papiExpansion = exp;
                    getLogger().info("PlaceholderAPI bağlantısı hazır (%afk_...%).");
                }
            } catch (Throwable t) {
                papi = false;
                getLogger().warning("PlaceholderAPI bağlantısı kurulamadı: " + t);
            }
        }

        getLogger().info("AfkAlan " + getDescription().getVersion() + " aktif. Alan: " + zones.all().size()
                + ", market ürünü: " + market.filledSlots().size() + ". [Jestron]");

        if (System.getProperty("afkalan.selftest") != null) {
            Bukkit.getScheduler().runTaskLater(this, () -> new SelfTest(this).run(), 40L);
        }
    }

    @Override
    public void onDisable() {
        if (tracker != null) tracker.stop();
        if (market != null) market.closeAll();
        if (papiExpansion != null) {
            try {
                ((PapiExpansion) papiExpansion).unregister();
            } catch (Throwable ignored) {
                // eski PlaceholderAPI sürümü
            }
            papiExpansion = null;
        }
        if (balances != null) balances.shutdown();
        getLogger().info("AfkAlan kapatıldı, veriler kaydedildi.");
    }

    /** /afk reload: config + alanlar.yml + afkmarket.yml + bakiyeler.yml */
    public void reload(CommandSender sender) {
        reloadConfig();
        settings.load(papi);
        List<String> errors = new ArrayList<>();
        String e;
        if ((e = zones.load()) != null) errors.add("alanlar.yml (" + e + ")");
        if ((e = market.load()) != null) errors.add("afkmarket.yml (" + e + ")");
        if ((e = balances.reload()) != null) errors.add("bakiyeler.yml (" + e + ")");
        balances.startAutoSave(settings.autosaveMinutes);
        if (!worldEdit.ready()) worldEdit.init();
        tracker.resync(true);
        market.refreshAll();
        if (errors.isEmpty()) {
            settings.send(sender, "reload-tamam");
        } else {
            settings.send(sender, "reload-hata", "{hata}", String.join(", ", errors));
        }
    }

    public void playSound(Player p, String sound) {
        if (sound == null || sound.isEmpty()) return;
        try {
            p.playSound(p.getLocation(), sound, 1F, 1F);
        } catch (Throwable ignored) {
            // geçersiz ses anahtarı
        }
    }

    public Settings settings() {
        return settings;
    }

    public ZoneManager zones() {
        return zones;
    }

    public BalanceManager balances() {
        return balances;
    }

    public MarketManager market() {
        return market;
    }

    public AfkTracker tracker() {
        return tracker;
    }

    public WorldEditHook worldEdit() {
        return worldEdit;
    }

    public boolean papiEnabled() {
        return papi;
    }
}
