package dev.opitems;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Wraith Armor full-set bonuses. */
public final class ArmorListener implements Listener {

    private static final double DAMAGE_MULTIPLIER = 0.75; // 25% less damage

    private final Set<UUID> wearers = new HashSet<>();

    public ArmorListener(JavaPlugin plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 10L);
    }

    public static boolean hasFullSet(Player p) {
        PlayerInventory inv = p.getInventory();
        return Items.is(inv.getHelmet(), Items.WRAITH_HELMET)
                && Items.is(inv.getChestplate(), Items.WRAITH_CHESTPLATE)
                && Items.is(inv.getLeggings(), Items.WRAITH_LEGGINGS)
                && Items.is(inv.getBoots(), Items.WRAITH_BOOTS);
    }

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            if (hasFullSet(p)) {
                wearers.add(id);
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 1, true, false, false));
                p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 60, 0, true, false, false));
                p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 60, 0, true, false, false));
                p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 260, 0, true, false, false));
            } else if (wearers.remove(id)) {
                p.removePotionEffect(PotionEffectType.SPEED);
                p.removePotionEffect(PotionEffectType.JUMP_BOOST);
                p.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
                p.removePotionEffect(PotionEffectType.NIGHT_VISION);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (!hasFullSet(p)) return;

        if (e.getCause() == EntityDamageEvent.DamageCause.FALL) {
            e.setCancelled(true);
            return;
        }
        e.setDamage(e.getDamage() * DAMAGE_MULTIPLIER);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        wearers.remove(e.getPlayer().getUniqueId());
    }
}
