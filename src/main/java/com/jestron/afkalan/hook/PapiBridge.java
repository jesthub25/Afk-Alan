package com.jestron.afkalan.hook;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;

/** PlaceholderAPI çağrıları. Bu sınıf yalnızca PlaceholderAPI kuruluysa yüklenir. */
public final class PapiBridge {

    private PapiBridge() {
    }

    public static String apply(Player player, String text) {
        try {
            return PlaceholderAPI.setPlaceholders(player, text);
        } catch (Throwable t) {
            return text;
        }
    }
}
