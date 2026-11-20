package com.boxpvp.abilities;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Zona melingkar: musuh di dalam zona tidak bisa terbang dengan Elytra.
 * Pemilik zona tetap bisa memakai Elytra.
 */
public final class ElytraZoneAbility implements Listener {

    private static final String ID = AbilityItems.ELYTRA_ZONE;

    private record Zone(UUID owner, Location center, double radius, double height, long expire) {
    }

    private final BoxPVPAbilities plugin;
    private final List<Zone> zones = new ArrayList<>();
    private BukkitTask task;
    private double angle = 0;

    public ElytraZoneAbility(BoxPVPAbilities plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!event.getAction().isRightClick()) return;
        if (!plugin.getAbilityItems().isAbility(event.getItem(), ID)) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        CooldownManager cooldowns = plugin.getCooldownManager();

        if (cooldowns.denyIfCooling(player, ID)) return;

        FileConfiguration cfg = plugin.getConfig();
        int cooldown = cfg.getInt("elytra-zone.cooldown", 45);
        int duration = cfg.getInt("elytra-zone.duration", 12);
        double radius = cfg.getDouble("elytra-zone.radius", 6.0);
        double height = cfg.getDouble("elytra-zone.height", 25.0);

        Location center = player.getLocation().clone();
        zones.add(new Zone(player.getUniqueId(), center, radius, height,
                System.currentTimeMillis() + duration * 1000L));
        ensureTask();

        center.getWorld().playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 1.0F, 1.5F);
        cooldowns.start(player, ID, "Elytra Zone", cooldown, Material.FEATHER);
        player.sendMessage(plugin.msg("activated", "ability", "Elytra Zone"));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent event) {
        if (!event.isGliding()) return;
        if (!(event.getEntity() instanceof Player player)) return;

        if (insideEnemyZone(player)) {
            event.setCancelled(true);
            player.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacySection().deserialize(plugin.msgRaw("elytra-blocked")));
        }
    }

    private void ensureTask() {
        if (task != null) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 3L);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        angle += 0.12;

        Iterator<Zone> it = zones.iterator();
        while (it.hasNext()) {
            Zone zone = it.next();
            if (now > zone.expire()) {
                it.remove();
                continue;
            }

            drawRing(zone);

            // Paksa turun musuh yang sudah terlanjur terbang saat zona muncul
            World world = zone.center().getWorld();
            if (world == null) continue;
            for (Player target : world.getPlayers()) {
                if (target.getUniqueId().equals(zone.owner())) continue;
                if (target.isGliding() && inside(zone, target.getLocation())) {
                    target.setGliding(false);
                }
            }
        }

        if (zones.isEmpty() && task != null) {
            task.cancel();
            task = null;
        }
    }

    private void drawRing(Zone zone) {
        World world = zone.center().getWorld();
        if (world == null) return;

        FileConfiguration cfg = plugin.getConfig();
        int points = Math.max(16, cfg.getInt("elytra-zone.particle-points", 60));
        Color color = Color.fromRGB(
                clamp(cfg.getInt("elytra-zone.color.red", 255)),
                clamp(cfg.getInt("elytra-zone.color.green", 50)),
                clamp(cfg.getInt("elytra-zone.color.blue", 50)));
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.4F);

        double[] heights = {0.15, 1.3};
        for (double h : heights) {
            for (int i = 0; i < points; i++) {
                double rad = (Math.PI * 2 * i) / points + angle;
                Location p = zone.center().clone().add(
                        Math.cos(rad) * zone.radius(), h, Math.sin(rad) * zone.radius());
                world.spawnParticle(Particle.REDSTONE, p, 1, 0, 0, 0, 0, dust, true);
            }
        }
    }

    private boolean insideEnemyZone(Player player) {
        long now = System.currentTimeMillis();
        for (Zone zone : zones) {
            if (now > zone.expire()) continue;
            if (zone.owner().equals(player.getUniqueId())) continue;
            if (inside(zone, player.getLocation())) return true;
        }
        return false;
    }

    private boolean inside(Zone zone, Location loc) {
        Location c = zone.center();
        if (c.getWorld() == null || !c.getWorld().equals(loc.getWorld())) return false;
        double dx = loc.getX() - c.getX();
        double dz = loc.getZ() - c.getZ();
        double dy = loc.getY() - c.getY();
        return dx * dx + dz * dz <= zone.radius() * zone.radius()
                && dy >= -3.0 && dy <= zone.height();
    }

    private int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    public void shutdown() {
        if (task != null) task.cancel();
        task = null;
        zones.clear();
    }
}
