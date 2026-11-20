package com.boxpvp.abilities;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Crossbow multishot. Panah yang menancap di tanah/blok membuat cobweb 3x3.
 * Cobweb hanya menggantikan AIR (tidak merusak bangunan) dan hilang otomatis.
 */
public final class WebTrapAbility implements Listener {

    private static final String ID = AbilityItems.WEB_TRAP;

    private final BoxPVPAbilities plugin;
    private final NamespacedKey projectileKey;
    private final List<List<Block>> activeWebs = new ArrayList<>();

    public WebTrapAbility(BoxPVPAbilities plugin) {
        this.plugin = plugin;
        this.projectileKey = new NamespacedKey(plugin, "web_trap_arrow");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getBow() == null || !plugin.getAbilityItems().isAbility(event.getBow(), ID)) return;
        if (!(event.getProjectile() instanceof AbstractArrow mainArrow)) return;

        CooldownManager cooldowns = plugin.getCooldownManager();
        if (cooldowns.denyIfCooling(player, ID)) return; // tembak biasa tanpa efek

        tag(mainArrow);

        // Multishot: 2 panah tambahan kiri & kanan
        double sideAngle = plugin.getConfig().getDouble("web-trap.side-arrow-angle", 12.0);
        Vector base = mainArrow.getVelocity();
        World world = player.getWorld();

        for (double deg : new double[]{-sideAngle, sideAngle}) {
            Arrow extra = world.spawn(mainArrow.getLocation(), Arrow.class);
            extra.setShooter(player);
            extra.setVelocity(base.clone().rotateAroundY(Math.toRadians(deg)));
            extra.setCritical(mainArrow.isCritical());
            extra.setDamage(mainArrow.getDamage());
            extra.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            tag(extra);
        }

        cooldowns.start(player, ID, "Web Trap", plugin.getConfig().getInt("web-trap.cooldown", 30), null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof AbstractArrow arrow)) return;
        if (!arrow.getPersistentDataContainer().has(projectileKey, PersistentDataType.BYTE)) return;

        Block hitBlock = event.getHitBlock();
        if (hitBlock == null) return; // hanya aktif kalau kena tanah/blok

        BlockFace face = event.getHitBlockFace();
        Block center = face != null ? hitBlock.getRelative(face) : hitBlock;

        createWeb(center);
        arrow.remove();
    }

    private void tag(AbstractArrow arrow) {
        arrow.getPersistentDataContainer().set(projectileKey, PersistentDataType.BYTE, (byte) 1);
    }

    private void createWeb(Block center) {
        World world = center.getWorld();
        int size = Math.max(1, plugin.getConfig().getInt("web-trap.web-size", 3));
        int half = size / 2;
        int durationTicks = Math.max(1, plugin.getConfig().getInt("web-trap.web-duration", 8)) * 20;

        List<Block> placed = new ArrayList<>();
        for (int x = -half; x <= half; x++) {
            for (int y = -half; y <= half; y++) {
                for (int z = -half; z <= half; z++) {
                    Block block = center.getRelative(x, y, z);
                    if (block.getY() < world.getMinHeight() || block.getY() >= world.getMaxHeight()) continue;
                    if (block.getType() != Material.AIR) continue; // jangan timpa bangunan
                    block.setType(Material.COBWEB, false);
                    placed.add(block);
                }
            }
        }

        if (placed.isEmpty()) return;

        Location loc = center.getLocation().add(0.5, 0.5, 0.5);
        world.spawnParticle(Particle.CLOUD, loc, 35, half + 0.3, 0.6, half + 0.3, 0.02);
        world.playSound(loc, Sound.BLOCK_WOOL_PLACE, 1.0F, 0.8F);

        activeWebs.add(placed);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            removeWeb(placed);
            activeWebs.remove(placed);
        }, durationTicks);
    }

    private void removeWeb(List<Block> blocks) {
        for (Block block : blocks) {
            if (block.getType() == Material.COBWEB) {
                block.setType(Material.AIR, false);
            }
        }
    }

    public void shutdown() {
        for (List<Block> web : new ArrayList<>(activeWebs)) removeWeb(web);
        activeWebs.clear();
    }
}
