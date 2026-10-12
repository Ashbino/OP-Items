package dev.opitems;

import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public final class WeaponListener implements Listener {

    // ---------- Wither Scythe: lifesteal + wither on hit ----------

    @EventHandler(ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (!(e.getEntity() instanceof LivingEntity victim)) return;
        if (!Items.is(p.getInventory().getItemInMainHand(), Items.WITHER_SCYTHE)) return;

        victim.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 100, 2)); // Wither III, 5s

        double heal = e.getFinalDamage() * 0.30;
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = max == null ? 20.0 : max.getValue();
        p.setHealth(Math.min(maxHealth, p.getHealth() + heal));
        p.getWorld().spawnParticle(Particle.SOUL, victim.getLocation().add(0, 1, 0), 8, 0.3, 0.5, 0.3, 0.05);
    }

    // ---------- right-click abilities ----------

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player p = e.getPlayer();
        Items type = Items.fromStack(p.getInventory().getItemInMainHand());
        if (type == null) return;

        Block clicked = e.getClickedBlock();
        if (clicked != null && clicked.getType().isInteractable() && !p.isSneaking()) return;

        switch (type) {
            case WITHER_SCYTHE -> { e.setCancelled(true); soulWave(p); }
            case THUNDER_HAMMER -> { e.setCancelled(true); thunder(p); }
            case BLINK_BLADE -> { e.setCancelled(true); blink(p); }
            default -> { }
        }
    }

    private static boolean canHurt(LivingEntity le, Player self) {
        if (le.equals(self) || le instanceof ArmorStand) return false;
        return !(le instanceof Player tp
                && (tp.getGameMode() == GameMode.CREATIVE || tp.getGameMode() == GameMode.SPECTATOR));
    }

    private void soulWave(Player p) {
        if (!Cooldowns.tryUse(p, "scythe", 10_000)) return;

        World w = p.getWorld();
        Location c = p.getLocation();

        for (LivingEntity le : w.getNearbyLivingEntities(c, 6)) {
            if (!canHurt(le, p)) continue;
            le.damage(8.0, p);
            le.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 100, 2));
            Vector kb = le.getLocation().toVector().subtract(c.toVector());
            kb.setY(0);
            if (kb.lengthSquared() > 0) {
                kb.normalize().multiply(1.2);
            }
            kb.setY(0.45);
            le.setVelocity(kb);
        }

        for (double r = 1.0; r <= 6.0; r += 1.25) {
            for (double a = 0; a < Math.PI * 2; a += 0.2) {
                w.spawnParticle(Particle.SOUL, c.clone().add(Math.cos(a) * r, 0.3, Math.sin(a) * r), 1, 0, 0, 0, 0);
            }
        }
        w.playSound(c, Sound.ENTITY_WITHER_SPAWN, 0.6f, 1.6f);
        w.playSound(c, Sound.PARTICLE_SOUL_ESCAPE, 1.5f, 0.5f);
    }

    private void thunder(Player p) {
        if (!Cooldowns.isReady(p, "hammer")) return;

        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        RayTraceResult r = w.rayTrace(eye, eye.getDirection(), 40, FluidCollisionMode.NEVER, true, 0.5,
                ent -> ent instanceof LivingEntity && !ent.equals(p));
        if (r == null) {
            p.sendActionBar(net.kyori.adventure.text.Component.text("No target in range",
                    net.kyori.adventure.text.format.NamedTextColor.RED));
            return;
        }
        Cooldowns.start(p, "hammer", 6_000);

        Location target = r.getHitPosition().toLocation(w);
        w.strikeLightningEffect(target); // visual + sound only, so the wielder is never hit

        for (LivingEntity le : w.getNearbyLivingEntities(target, 3.5)) {
            if (!canHurt(le, p)) continue;
            le.damage(12.0, p);
            le.setFireTicks(100);
        }
        w.spawnParticle(Particle.ELECTRIC_SPARK, target, 80, 1.5, 0.5, 1.5, 0.3);
    }

    private void blink(Player p) {
        if (!Cooldowns.isReady(p, "blink")) return;

        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        double max = 12.0;

        RayTraceResult hit = w.rayTraceBlocks(eye, dir, max, FluidCollisionMode.NEVER, true);
        double dist = hit == null ? max : Math.max(0, hit.getHitPosition().distance(eye.toVector()) - 0.6);

        Location dest = null;
        for (double d = dist; d >= 1.0; d -= 0.25) {
            Location feet = eye.clone().add(dir.clone().multiply(d)).subtract(0, 1.62, 0);
            if (feet.getBlock().isPassable()
                    && feet.clone().add(0, 1, 0).getBlock().isPassable()
                    && feet.clone().add(0, 1.8, 0).getBlock().isPassable()) {
                dest = feet;
                break;
            }
        }
        if (dest == null) {
            p.sendActionBar(net.kyori.adventure.text.Component.text("No room to blink",
                    net.kyori.adventure.text.format.NamedTextColor.RED));
            return;
        }
        Cooldowns.start(p, "blink", 2_000);

        Location from = p.getLocation();
        w.spawnParticle(Particle.REVERSE_PORTAL, from.clone().add(0, 1, 0), 40, 0.3, 0.6, 0.3, 0.2);
        p.teleport(dest);
        p.setFallDistance(0);
        w.spawnParticle(Particle.REVERSE_PORTAL, dest.clone().add(0, 1, 0), 40, 0.3, 0.6, 0.3, 0.2);
        w.playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);
    }
}
