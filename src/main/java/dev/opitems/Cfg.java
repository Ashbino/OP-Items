package dev.opitems;

import org.bukkit.plugin.java.JavaPlugin;

/** Tiny static wrapper so listeners can read live config values. */
public final class Cfg {

    private static JavaPlugin plugin;

    private Cfg() { }

    public static void init(JavaPlugin p) {
        plugin = p;
    }

    public static double d(String path) {
        return plugin.getConfig().getDouble(path);
    }

    public static int i(String path) {
        return plugin.getConfig().getInt(path);
    }

    public static boolean b(String path) {
        return plugin.getConfig().getBoolean(path);
    }

    /** Seconds in config -> milliseconds. */
    public static long ms(String path) {
        return Math.round(d(path) * 1000.0);
    }

    /** Seconds in config -> ticks. */
    public static int ticks(String path) {
        return (int) Math.round(d(path) * 20.0);
    }

    /** 1-based level in config -> potion amplifier; returns -1 when disabled (0). */
    public static int amplifier(String path) {
        int level = i(path);
        return level <= 0 ? -1 : level - 1;
    }
}
