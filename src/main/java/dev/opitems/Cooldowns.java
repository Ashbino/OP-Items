package dev.opitems;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public final class Cooldowns {

    private static final Map<String, Long> UNTIL = new HashMap<>();

    private Cooldowns() { }

    private static String k(Player p, String key) {
        return p.getUniqueId() + ":" + key;
    }

    /** Returns true (and starts the cooldown) if the ability is ready, otherwise tells the player. */
    public static boolean tryUse(Player p, String key, long millis) {
        if (!isReady(p, key)) return false;
        start(p, key, millis);
        return true;
    }

    /** Returns true if ready; otherwise shows the remaining time on the action bar. */
    public static boolean isReady(Player p, String key) {
        Long until = UNTIL.get(k(p, key));
        long now = System.currentTimeMillis();
        if (until != null && until > now) {
            p.sendActionBar(Component.text(
                    String.format("On cooldown: %.1fs", (until - now) / 1000.0), NamedTextColor.RED));
            return false;
        }
        return true;
    }

    public static void start(Player p, String key, long millis) {
        UNTIL.put(k(p, key), System.currentTimeMillis() + millis);
    }
}
