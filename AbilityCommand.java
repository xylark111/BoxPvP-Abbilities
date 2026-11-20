package com.boxpvp.abilities;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AbilityCommand implements CommandExecutor, TabCompleter {

    private final BoxPVPAbilities plugin;

    public AbilityCommand(BoxPVPAbilities plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("boxpvpabilities.admin")) {
            sender.sendMessage(plugin.msg("no-permission"));
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            sender.sendMessage(plugin.msg("reloaded"));
            return true;
        }

        if (args.length < 2 || !args[0].equalsIgnoreCase("give")) {
            sender.sendMessage(BoxPVPAbilities.color("&e/" + label + " give <ability> [player]  &7| &e/" + label + " reload"));
            return true;
        }

        Player target;
        if (args.length >= 3) {
            target = Bukkit.getPlayerExact(args[2]);
        } else {
            target = sender instanceof Player p ? p : null;
        }

        if (target == null) {
            sender.sendMessage(plugin.msg("player-not-found"));
            return true;
        }

        ItemStack item = plugin.getAbilityItems().create(args[1].toLowerCase(Locale.ROOT));
        if (item == null) {
            sender.sendMessage(plugin.msg("unknown-ability",
                    "list", String.join(", ", AbilityItems.COMMAND_KEYS)));
            return true;
        }

        target.getInventory().addItem(item).values()
                .forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));

        sender.sendMessage(plugin.msg("given", "ability", args[1], "player", target.getName()));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("boxpvpabilities.admin")) return out;

        if (args.length == 1) {
            addMatches(out, List.of("give", "reload"), args[0]);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            addMatches(out, AbilityItems.COMMAND_KEYS, args[1]);
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            Bukkit.getOnlinePlayers().forEach(p -> {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[2].toLowerCase(Locale.ROOT))) {
                    out.add(p.getName());
                }
            });
        }
        return out;
    }

    private void addMatches(List<String> out, List<String> options, String typed) {
        for (String option : options) {
            if (option.startsWith(typed.toLowerCase(Locale.ROOT))) out.add(option);
        }
    }
}
