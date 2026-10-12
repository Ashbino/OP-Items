package dev.opitems;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class OPItemsPlugin extends JavaPlugin implements CommandExecutor, TabCompleter {

    private static final String SET_ID = "wraith_set";

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Cfg.init(this);
        Items.init(this);

        Bukkit.getPluginManager().registerEvents(new SkeletonStaffListener(this), this);
        Bukkit.getPluginManager().registerEvents(new WeaponListener(), this);
        Bukkit.getPluginManager().registerEvents(new ArmorListener(this), this);

        var cmd = getCommand("opitems");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
        getLogger().info("OPItems enabled.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            StringBuilder sb = new StringBuilder("Items: ");
            for (Items i : Items.values()) sb.append(i.name().toLowerCase(Locale.ROOT)).append(", ");
            sb.append(SET_ID);
            sender.sendMessage(Component.text(sb.toString(), NamedTextColor.AQUA));
            sender.sendMessage(Component.text("Usage: /opitems <give|reload> ...", NamedTextColor.GRAY));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            sender.sendMessage(Component.text(
                    "OPItems config reloaded. Re-give items to pick up changed item stats.", NamedTextColor.GREEN));
            return true;
        }

        if (!args[0].equalsIgnoreCase("give")) {
            sender.sendMessage(Component.text("Usage: /opitems <list|give|reload>", NamedTextColor.RED));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /opitems give <item> [player]", NamedTextColor.RED));
            return true;
        }

        Player target;
        if (args.length >= 3) {
            target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                return true;
            }
        } else if (sender instanceof Player self) {
            target = self;
        } else {
            sender.sendMessage(Component.text("Specify a player.", NamedTextColor.RED));
            return true;
        }

        String id = args[1].toLowerCase(Locale.ROOT);
        if (id.equals(SET_ID)) {
            give(target, Items.WRAITH_HELMET);
            give(target, Items.WRAITH_CHESTPLATE);
            give(target, Items.WRAITH_LEGGINGS);
            give(target, Items.WRAITH_BOOTS);
            sender.sendMessage(Component.text("Gave the Wraith set to " + target.getName(), NamedTextColor.GREEN));
            return true;
        }

        Items item = Items.byName(id);
        if (item == null) {
            sender.sendMessage(Component.text("Unknown item. Use /opitems list", NamedTextColor.RED));
            return true;
        }
        give(target, item);
        sender.sendMessage(Component.text("Gave " + id + " to " + target.getName(), NamedTextColor.GREEN));
        return true;
    }

    private void give(Player target, Items item) {
        ItemStack stack = item.create();
        target.getInventory().addItem(stack).values()
                .forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.add("give");
            out.add("list");
            out.add("reload");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            for (Items i : Items.values()) out.add(i.name().toLowerCase(Locale.ROOT));
            out.add(SET_ID);
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
