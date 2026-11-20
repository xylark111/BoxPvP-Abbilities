package com.boxpvp.abilities;

import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class BoxPVPAbilities extends JavaPlugin {

    private AbilityItems abilityItems;
    private CooldownManager cooldownManager;

    private ElytraZoneAbility elytraZone;
    private WebTrapAbility webTrap;
    private TridentBlastAbility tridentBlast;
    private TimeStopAbility timeStop;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        abilityItems = new AbilityItems(this);
        cooldownManager = new CooldownManager(this);
        cooldownManager.start();

        elytraZone = new ElytraZoneAbility(this);
        webTrap = new WebTrapAbility(this);
        tridentBlast = new TridentBlastAbility(this);
        timeStop = new TimeStopAbility(this);
        timeStop.start();

        getServer().getPluginManager().registerEvents(elytraZone, this);
        getServer().getPluginManager().registerEvents(webTrap, this);
        getServer().getPluginManager().registerEvents(tridentBlast, this);
        getServer().getPluginManager().registerEvents(timeStop, this);

        PluginCommand command = getCommand("ability");
        if (command != null) {
            AbilityCommand executor = new AbilityCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        getLogger().info("BoxPVPAbilities aktif.");
    }

    @Override
    public void onDisable() {
        if (elytraZone != null) elytraZone.shutdown();
        if (webTrap != null) webTrap.shutdown();
        if (timeStop != null) timeStop.shutdown();
        if (cooldownManager != null) cooldownManager.stop();
    }

    public AbilityItems getAbilityItems() {
        return abilityItems;
    }

    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }

    /** Ambil pesan dari config (dengan prefix) dan ganti placeholder {key} -> value. */
    public String msg(String key, String... pairs) {
        String text = getConfig().getString("messages." + key, key);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            text = text.replace("{" + pairs[i] + "}", pairs[i + 1]);
        }
        return color(getConfig().getString("messages.prefix", "") + text);
    }

    /** Pesan tanpa prefix (untuk action bar). */
    public String msgRaw(String key, String... pairs) {
        String text = getConfig().getString("messages." + key, key);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            text = text.replace("{" + pairs[i] + "}", pairs[i + 1]);
        }
        return color(text);
    }

    public static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
