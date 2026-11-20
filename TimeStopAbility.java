package com.boxpvp.abilities;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Time Stop: gelombang particle melebar dari tengah. Musuh yang tersentuh gelombang
 * membeku total (tidak bisa jalan/lompat/teleport/elytra). Kamera tetap bisa digerakkan
 * dan mereka tetap bisa memukul. Pemakai tidak terpengaruh.
 */
public final class TimeStopAbility implements Listener {

    private static final String ID = AbilityItems.TIME_STOP;

    private record FrozenMob(Mob mob, long expire) {
    }

    private final BoxPVPAbilities plugin;
    private final Map<UUID, Long> frozenPlayers = new HashMap<>();
    private final Map<UUID, FrozenMob> frozenMobs = new HashMap<>();
    private BukkitTask releaseTask;

    public TimeStopAbility(BoxPVPAbilities plugin) {
        this.plugin = plugin;
    }

    public void start() {
        releaseTask = Bukkit.getScheduler().runTaskTimer(plugin, this::releaseExpired, 5L, 5L);
    }

    public void shutdown() {
        if (releaseTask != null) releaseTask.cancel();
        for (UUID id : new ArrayList<>(frozenPlayers.keySet())) releasePlayer(id);
        for (UUID id : new ArrayList<>(frozenMobs.keySet())) releaseMob(id);
    }

    // ------------------------------------------------------------ aktivasi

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!event.getAction().isRightClick()) return;
        if (!plugin.getAbilityItems().isAbility(event.getItem(), ID)) return;

        event.setCancelled(true);
        Player owner = event.getPlayer();
        CooldownManager cooldowns = plugin.getCooldownManager();

        if (cooldowns.denyIfCooling(owner, ID)) return;

        int cooldown = plugin.getConfig().getInt("time-stop.cooldown", 90);
        cooldowns.start(owner, ID, "Time Stop", cooldown, Material.CLOCK);
        owner.sendMessage(plugin.msg("activated", "ability", "Time Stop"));

        startWave(owner, owner.getLocation().clone());
    }

    private void startWave(Player owner, Location center) {
        World world = center.getWorld();
        if (world == null) return;

        double radius = plugin.getConfig().getDouble("time-stop.radius", 9.0);
        int expandTicks = Math.max(1, plugin.getConfig().getInt("time-stop.expand-time", 3)) * 20;
        int freezeSeconds = Math.max(1, plugin.getConfig().getInt("time-stop.freeze-duration", 6));
        boolean freezeMobs = plugin.getConfig().getBoolean("time-stop.freeze-mobs", true);

        world.playSound(center, Sound.BLOCK_BEACON_DEACTIVATE, 1.5F, 0.5F);
        world.playSound(center, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6F, 1.6F);
        world.spawnParticle(Particle.FLASH, center.clone().add(0, 1, 0), 1, 0, 0, 0, 0);

        Set<UUID> affected = new HashSet<>();
        Particle.DustOptions purple = new Particle.DustOptions(Color.fromRGB(150, 40, 220), 1.5F);
        Particle.DustOptions white = new Particle.DustOptions(Color.fromRGB(235, 235, 255), 1.2F);

        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                double progress = Math.min(1.0, (double) tick / expandTicks);
                double r = Math.max(0.5, radius * progress);

                // gelombang melebar (2 cincin)
                int points = (int) (r * 5) + 12;
                for (double h : new double[]{0.15, 1.2}) {
                    for (int i = 0; i < points; i++) {
                        double rad = (Math.PI * 2 * i) / points;
                        Location p = center.clone().add(Math.cos(rad) * r, h, Math.sin(rad) * r);
                        world.spawnParticle(Particle.REDSTONE, p, 1, 0, 0, 0, 0,
                                (i % 3 == 0) ? white : purple, true);
                        if (i % 6 == 0) {
                            world.spawnParticle(Particle.END_ROD, p, 1, 0, 0.3, 0, 0.01, null, true);
                        }
                    }
                }
                world.spawnParticle(Particle.SPELL_WITCH, center.clone().add(0, 1, 0),
                        12, r * 0.3, 0.8, r * 0.3, 0.02);

                // bekukan musuh yang tersentuh gelombang
                for (Entity entity : world.getNearbyEntities(center, r, r, r)) {
                    if (!(entity instanceof LivingEntity living)) continue;
                    if (entity.getUniqueId().equals(owner.getUniqueId())) continue;
                    if (affected.contains(entity.getUniqueId())) continue;
                    if (entity.getLocation().distanceSquared(center) > r * r) continue;

                    long expire = System.currentTimeMillis() + freezeSeconds * 1000L;

                    if (living instanceof Player target) {
                        if (target.getGameMode() == GameMode.SPECTATOR) continue;
                        affected.add(entity.getUniqueId());
                        freezePlayer(target, expire);
                    } else if (freezeMobs && living instanceof Mob mob) {
                        affected.add(entity.getUniqueId());
                        freezeMob(mob, expire);
                    }
                }

                tick += 2;
                if (tick > expandTicks) cancel();
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    // ------------------------------------------------------------ freeze / release

    private void freezePlayer(Player target, long expire) {
        frozenPlayers.put(target.getUniqueId(), expire);
        target.setVelocity(new Vector(0, 0, 0));
        target.setGravity(false);
        if (target.isGliding()) target.setGliding(false);
        target.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection().deserialize(plugin.msgRaw("time-stopped")));
        target.playSound(target.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.0F, 0.5F);
    }

    private void freezeMob(Mob mob, long expire) {
        frozenMobs.put(mob.getUniqueId(), new FrozenMob(mob, expire));
        mob.setAware(false);
        mob.setVelocity(new Vector(0, 0, 0));
    }

    private void releasePlayer(UUID id) {
        frozenPlayers.remove(id);
        Player player = Bukkit.getPlayer(id);
        if (player != null) {
            player.setGravity(true);
            player.setFallDistance(0F);
        }
    }

    private void releaseMob(UUID id) {
        FrozenMob frozen = frozenMobs.remove(id);
        if (frozen != null && frozen.mob().isValid()) {
            frozen.mob().setAware(true);
        }
    }

    private void releaseExpired() {
        long now = System.currentTimeMillis();

        Iterator<Map.Entry<UUID, Long>> pi = frozenPlayers.entrySet().iterator();
        java.util.List<UUID> playersDone = new ArrayList<>();
        while (pi.hasNext()) {
            Map.Entry<UUID, Long> e = pi.next();
            if (now >= e.getValue()) playersDone.add(e.getKey());
        }
        playersDone.forEach(this::releasePlayer);

        java.util.List<UUID> mobsDone = new ArrayList<>();
        for (Map.Entry<UUID, FrozenMob> e : frozenMobs.entrySet()) {
            if (now >= e.getValue().expire() || !e.getValue().mob().isValid()) mobsDone.add(e.getKey());
        }
        mobsDone.forEach(this::releaseMob);
    }

    private boolean isFrozen(UUID id) {
        Long expire = frozenPlayers.get(id);
        return expire != null && System.currentTimeMillis() < expire;
    }

    // ------------------------------------------------------------ pembatasan gerak

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!isFrozen(player.getUniqueId())) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            // posisi dikunci, kamera (yaw/pitch) tetap bebas
            event.setTo(new Location(from.getWorld(), from.getX(), from.getY(), from.getZ(),
                    to.getYaw(), to.getPitch()));
            player.setVelocity(new Vector(0, 0, 0));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!isFrozen(event.getPlayer().getUniqueId())) return;
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                || cause == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPearl(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.EnderPearl pearl
                && pearl.getShooter() instanceof Player shooter
                && isFrozen(shooter.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent event) {
        if (event.isGliding() && isFrozen(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------ keamanan state

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        releasePlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        releasePlayer(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Jaga-jaga kalau server crash saat pemain sedang membeku
        Player player = event.getPlayer();
        if (!isFrozen(player.getUniqueId()) && !player.hasGravity()) {
            player.setGravity(true);
        }
    }
}
