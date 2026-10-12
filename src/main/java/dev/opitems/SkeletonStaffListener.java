package dev.opitems;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

public final class SkeletonStaffListener implements Listener {

    // ----- tweak these -----
    private static final int CHARGE_TICKS = 60;              // 3 seconds
    private static final long COOLDOWN_MS = 6000L;           // after firing
    private static final double RANGE = 64.0;                // beam length in blocks
    private static final double DAMAGE_TAKEN_MULTIPLIER = 1.5; // while charging
    // -----------------------

    private static final String CD_KEY = "skeleton_staff";

    private final JavaPlugin plugin;
    private final Map<UUID, Charge> charges = new HashMap<>();

    private static final class Charge {
        int ticks;
        boolean ready;
        BukkitTask task;
    }

    public SkeletonStaffListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player p = e.getPlayer();
        if (!Items.is(p.getInventory().getItemInMainHand(), Items.SKELETON_STAFF)) return;

        // Don't hijack chests, doors, etc. unless sneaking.
        Block clicked = e.getClickedBlock();
        if (clicked != null && clicked.getType().isInteractable() && !p.isSneaking()) return;

        e.setCancelled(true);

        Charge charge = charges.get(p.getUniqueId());
        if (charge == null) {
            if (!Cooldowns.isReady(p, CD_KEY)) return;
            startCharge(p);
        } else if (charge.ready) {
            fire(p);
        }
        // already charging but not ready: ignore
    }

    // ---------- charging ----------

    private void startCharge(Player p) {
        UUID id = p.getUniqueId();
        Charge c = new Charge();
        charges.put(id, c);

        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 0.6f);

        c.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!p.isOnline() || p.isDead()
                    || !Items.is(p.getInventory().getItemInMainHand(), Items.SKELETON_STAFF)) {
                abort(id, p, true);
                return;
            }

            c.ticks++;
            World w = p.getWorld();
            Location loc = p.getLocation();

            // swirling souls
            double angle = c.ticks * 0.45;
            for (int i = 0; i < 2; i++) {
                double a = angle + i * Math.PI;
                Location pl = loc.clone().add(Math.cos(a) * 1.0, 0.2 + (c.ticks % 25) / 14.0, Math.sin(a) * 1.0);
                w.spawnParticle(Particle.SOUL, pl, 1, 0, 0, 0, 0);
            }

            if (!c.ready) {
                if (c.ticks % 10 == 0) {
                    w.playSound(loc, Sound.PARTICLE_SOUL_ESCAPE, 1f, 0.5f + 1.0f * c.ticks / CHARGE_TICKS);
                }
                if (c.ticks >= CHARGE_TICKS) {
                    c.ready = true;
                    w.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.5f);
                    w.spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.02);
                }
                int filled = (int) (20.0 * c.ticks / CHARGE_TICKS);
                p.sendActionBar(Component.text("Charging ", NamedTextColor.AQUA)
                        .append(Component.text("|".repeat(Math.min(filled, 20)), NamedTextColor.GREEN))
                        .append(Component.text("|".repeat(Math.max(0, 20 - filled)), NamedTextColor.DARK_GRAY))
                        .append(Component.text("  (1.5x damage taken)", NamedTextColor.RED)));
            } else {
                p.sendActionBar(Component.text("SOULS READY - right-click to fire!", NamedTextColor.AQUA));
            }
        }, 0L, 1L);
    }

    private void abort(UUID id, Player p, boolean notify) {
        Charge c = charges.remove(id);
        if (c == null) return;
        if (c.task != null) c.task.cancel();
        if (notify && p.isOnline()) {
            p.sendActionBar(Component.text("Charge cancelled", NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        abort(e.getPlayer().getUniqueId(), e.getPlayer(), false);
    }

    /** While charging (not yet ready) the holder takes extra damage. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Charge c = charges.get(p.getUniqueId());
        if (c == null || c.ready) return;
        e.setDamage(e.getDamage() * DAMAGE_TAKEN_MULTIPLIER);
        p.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, p.getLocation().add(0, 1, 0), 6, 0.3, 0.4, 0.3, 0.1);
    }

    // ---------- firing ----------

    private void fire(Player p) {
        abort(p.getUniqueId(), p, false);
        Cooldowns.start(p, CD_KEY, COOLDOWN_MS);

        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();

        Predicate<Entity> filter = ent -> ent instanceof LivingEntity
                && !ent.equals(p)
                && !(ent instanceof ArmorStand)
                && !(ent instanceof Player tp
                    && (tp.getGameMode() == GameMode.CREATIVE || tp.getGameMode() == GameMode.SPECTATOR));

        // Hits the first living entity OR stops at the first solid block.
        RayTraceResult res = w.rayTrace(eye, dir, RANGE, FluidCollisionMode.NEVER, true, 0.5, filter);
        double dist = res == null ? RANGE : res.getHitPosition().distance(eye.toVector());

        for (double d = 0.8; d <= dist; d += 0.35) {
            Location l = eye.clone().add(dir.clone().multiply(d)).subtract(0, 0.2, 0);
            w.spawnParticle(Particle.SOUL, l, 1, 0.04, 0.04, 0.04, 0);
            w.spawnParticle(Particle.SOUL_FIRE_FLAME, l, 1, 0, 0, 0, 0);
        }
        w.playSound(eye, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.2f, 1.5f);
        w.playSound(eye, Sound.ENTITY_WITHER_SHOOT, 0.8f, 1.8f);

        if (res != null && res.getHitEntity() instanceof LivingEntity target) {
            Location hit = target.getLocation().add(0, target.getHeight() / 2, 0);
            w.spawnParticle(Particle.SOUL_FIRE_FLAME, hit, 40, 0.4, 0.5, 0.4, 0.08);
            w.spawnParticle(Particle.SOUL, hit, 25, 0.4, 0.5, 0.4, 0.1);

            boolean died = soulKill(p, target);
            if (!died) {
                p.sendActionBar(Component.text("Blocked by a totem!", NamedTextColor.GOLD));
            }
        }
    }

    /**
     * One-shots the target. Creative/spectator players are immune, and a Totem of Undying
     * still triggers (the beam is lethal damage, it does not bypass totems).
     *
     * @return true if the target died
     */
    private static boolean soulKill(Player source, LivingEntity target) {
        if (target.isDead()) return true;
        if (target instanceof Player tp
                && (tp.getGameMode() == GameMode.CREATIVE || tp.getGameMode() == GameMode.SPECTATOR)) {
            return false;
        }

        boolean hadTotem = hasTotem(target);

        // Resistance would otherwise nullify the hit (and stop a totem from triggering).
        target.removePotionEffect(PotionEffectType.RESISTANCE);
        target.setNoDamageTicks(0);

        DamageSource ds = DamageSource.builder(DamageType.MAGIC)
                .withCausingEntity(source)
                .withDirectEntity(source)
                .build();
        target.damage(1000.0, ds);

        // Armor / absorption somehow soaked it and there's no totem to pop: force the kill.
        if (!target.isDead() && !hadTotem) {
            target.setHealth(0.0);
        }
        return target.isDead();
    }

    private static boolean hasTotem(LivingEntity e) {
        EntityEquipment eq = e.getEquipment();
        if (eq == null) return false;
        return eq.getItemInMainHand().getType() == Material.TOTEM_OF_UNDYING
                || eq.getItemInOffHand().getType() == Material.TOTEM_OF_UNDYING;
    }
}
