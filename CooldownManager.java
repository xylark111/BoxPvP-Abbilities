package com.boxpvp.abilities;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Cooldown per player per ability.
 * Visual: progress bar di action bar (saat memegang item ability) dan
 * animasi cooldown di item (untuk ability yang memakai item biasa).
 */
public final class CooldownManager {

    private record Entry(long end, long total, String display) {
    }

    private final BoxPVPAbilities plugin;
    private final Map<UUID, Map<String, Entry>> cooldowns = new HashMap<>();
    private BukkitTask task;

    public CooldownManager(BoxPVPAbilities plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 4L, 4L);
    }

    public void stop() {
        if (task != null) task.cancel();
        cooldowns.clear();
    }

    public boolean isOnCooldown(Player player, String id) {
        Map<String, Entry> map = cooldowns.get(player.getUniqueId());
        if (map == null) return false;
        Entry entry = map.get(id);
        return entry != null && entry.end() > System.currentTimeMillis();
    }

    /** True kalau masih cooldown (sekaligus menampilkan sisa waktu di action bar). */
    public boolean denyIfCooling(Player player, String id) {
        Map<String, Entry> map = cooldowns.get(player.getUniqueId());
        if (map == null) return false;
        Entry entry = map.get(id);
        if (entry == null) return false;
        long left = entry.end() - System.currentTimeMillis();
        if (left <= 0) return false;
        sendBar(player, "&c✖ &f" + entry.display() + " &7cooldown: &e" + format(left) + "s");
        return true;
    }

    public void start(Player player, String id, String display, int seconds, Material itemMaterial) {
        if (seconds <= 0) return;
        long total = seconds * 1000L;
        cooldowns.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
                .put(id, new Entry(System.currentTimeMillis() + total, total, display));
        if (itemMaterial != null) {
            player.setCooldown(itemMaterial, seconds * 20);
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Map<String, Entry>>> players = cooldowns.entrySet().iterator();

        while (players.hasNext()) {
            Map.Entry<UUID, Map<String, Entry>> pe = players.next();
            Player player = Bukkit.getPlayer(pe.getKey());

            Iterator<Map.Entry<String, Entry>> it = pe.getValue().entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Entry> en = it.next();
                Entry entry = en.getValue();
                long left = entry.end() - now;

                if (left <= 0) {
                    it.remove();
                    if (player != null) {
                        sendBar(player, "&a✔ &f" + entry.display() + " &asiap digunakan!");
                        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8F, 1.4F);
                    }
                    continue;
                }

                if (player != null && holding(player, en.getKey())) {
                    sendBar(player, bar(entry, left));
                }
            }

            if (pe.getValue().isEmpty()) players.remove();
        }
    }

    private boolean holding(Player player, String id) {
        AbilityItems items = plugin.getAbilityItems();
        return items.isAbility(player.getInventory().getItemInMainHand(), id)
                || items.isAbility(player.getInventory().getItemInOffHand(), id);
    }

    private String bar(Entry entry, long left) {
        int segments = 12;
        double progress = 1.0 - ((double) left / (double) entry.total());
        int filled = (int) Math.round(progress * segments);

        StringBuilder sb = new StringBuilder("&c").append(entry.display()).append(" &8[");
        for (int i = 0; i < segments; i++) {
            sb.append(i < filled ? "&a█" : "&c█");
        }
        sb.append("&8] &e").append(format(left)).append("s");
        return sb.toString();
    }

    private String format(long millis) {
        return String.format(Locale.US, "%.1f", millis / 1000.0);
    }

    private void sendBar(Player player, String text) {
        player.sendActionBar(LegacyComponentSerializer.legacySection()
                .deserialize(BoxPVPAbilities.color(text)));
    }
}
