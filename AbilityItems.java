package com.boxpvp.abilities;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Locale;

public final class AbilityItems {

    public static final String ELYTRA_ZONE = "elytra_zone";
    public static final String WEB_TRAP = "web_trap";
    public static final String TRIDENT_BLAST = "trident_blast";
    public static final String TIME_STOP = "time_stop";

    public static final List<String> COMMAND_KEYS =
            List.of("elytrazone", "webtrap", "tridentblast", "timestop");

    private final NamespacedKey abilityKey;

    public AbilityItems(BoxPVPAbilities plugin) {
        this.abilityKey = new NamespacedKey(plugin, "ability_id");
    }

    public ItemStack create(String key) {
        switch (key.toLowerCase(Locale.ROOT)) {
            case "elytrazone":
                return build(Material.FEATHER, ELYTRA_ZONE, "&b&lElytra Zone", List.of(
                        "&7Right-click untuk membuat zona di tempatmu berdiri.",
                        "&7Musuh di dalam zona tidak bisa memakai Elytra.",
                        "&7Kamu sendiri tetap bisa memakai Elytra."));
            case "webtrap":
                return build(Material.CROSSBOW, WEB_TRAP, "&a&lWeb Trap Crossbow", List.of(
                        "&7Tembak crossbow: multishot 3 panah.",
                        "&7Panah yang kena tanah membuat cobweb 3x3.",
                        "&7Cobweb hilang otomatis, bangunan tidak rusak."));
            case "tridentblast":
                return build(Material.TRIDENT, TRIDENT_BLAST, "&6&lTrident Blast", List.of(
                        "&7Lempar trident ke musuh.",
                        "&7Trident yang kena musuh meledak (damage saja).",
                        "&7Tidak merusak blok apapun."));
            case "timestop":
                return build(Material.CLOCK, TIME_STOP, "&5&lTime Stop", List.of(
                        "&7Right-click untuk menghentikan waktu.",
                        "&7Musuh di dalam zona membeku total.",
                        "&7Kamu tetap bebas bergerak."));
            default:
                return null;
        }
    }

    private ItemStack build(Material material, String id, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(BoxPVPAbilities.color(name));
        meta.setLore(lore.stream().map(BoxPVPAbilities::color).toList());
        meta.setUnbreakable(true);
        meta.addEnchant(Enchantment.LUCK, 1, true); // efek glow
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(abilityKey, PersistentDataType.STRING, id);

        item.setItemMeta(meta);
        return item;
    }

    public String getAbilityId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(abilityKey, PersistentDataType.STRING);
    }

    public boolean isAbility(ItemStack item, String id) {
        return id.equals(getAbilityId(item));
    }
}
