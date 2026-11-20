package com.boxpvp.abilities;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

/**
 * Trident yang kena musuh meledak: hanya damage + visual, TIDAK merusak blok.
 */
public final class TridentBlastAbility implements Listener {

    private static final String ID = AbilityItems.TRIDENT_BLAST;

    private final BoxPVPAbilities plugin;
    private final NamespacedKey projectileKey;

    public TridentBlastAbility(BoxPVPAbilities plugin) {
        this.plugin = plugin;
        this.projectileKey = new NamespacedKey(plugin, "trident_blast_projectile");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Trident trident)) return;
        if (!(trident.getShooter() instanceof Player player)) return;

        PlayerInventory inv = player.getInventory();
        boolean holding = plugin.getAbilityItems().isAbility(inv.getItemInMainHand(), ID)
                || plugin.getAbilityItems().isAbility(inv.getItemInOffHand(), ID);
        if (!holding) return;

        trident.getPersistentDataContainer().set(projectileKey, PersistentDataType.BYTE, (byte) 1);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Trident trident)) return;
        if (!trident.getPersistentDataContainer().has(projectileKey, PersistentDataType.BYTE)) return;
        if (!(event.getHitEntity() instanceof LivingEntity victim)) return;
        if (!(trident.getShooter() instanceof Player attacker)) return;
        if (victim.getUniqueId().equals(attacker.getUniqueId())) return;

        CooldownManager cooldowns = plugin.getCooldownManager();
        if (cooldowns.isOnCooldown(attacker, ID)) return;

        cooldowns.start(attacker, ID, "Trident Blast",
                plugin.getConfig().getInt("trident-blast.cooldown", 25), null);

        blast(attacker, victim.getLocation().add(0, victim.getHeight() / 2.0, 0));
    }

    private void blast(Player attacker, Location loc) {
        World world = loc.getWorld();
        if (world == null) return;

        double damage = plugin.getConfig().getDouble("trident-blast.damage", 8.0);
        double radius = plugin.getConfig().getDouble("trident-blast.radius", 3.0);

        // Visual (tidak ada world.createExplosion -> blok aman)
        world.spawnParticle(Particle.EXPLOSION_LARGE, loc, 2, radius * 0.2, radius * 0.2, radius * 0.2, 0);
        world.spawnParticle(Particle.FLAME, loc, 50, radius * 0.3, radius * 0.3, radius * 0.3, 0.1);
        world.spawnParticle(Particle.SMOKE_NORMAL, loc, 25, radius * 0.3, radius * 0.3, radius * 0.3, 0.05);
        for (int i = 0; i < 36; i++) {
            double rad = (Math.PI * 2 * i) / 36;
            world.spawnParticle(Particle.CLOUD,
                    loc.clone().add(Math.cos(rad) * radius, -0.3, Math.sin(rad) * radius),
                    1, 0, 0, 0, 0);
        }
        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0F, 1.2F);

        // Damage area
        for (Entity entity : world.getNearbyEntities(loc, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity target)) continue;
            if (target instanceof ArmorStand) continue;
            if (target.getUniqueId().equals(attacker.getUniqueId())) continue;
            if (target instanceof Player p && (p.getGameMode() == GameMode.SPECTATOR
                    || p.getGameMode() == GameMode.CREATIVE)) continue;
            if (target.getLocation().distanceSquared(loc) > radius * radius * 1.5) continue;

            target.damage(damage, attacker);
        }
    }
}
