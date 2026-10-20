package dev.quicktroll;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class QuickTroll extends JavaPlugin {

    private Freeze freeze;
    private TakeoverManager takeover;
    private MenuManager menus;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getLogger().info("Running on " + Bukkit.getName() + " " + Bukkit.getBukkitVersion() + " (Spigot API)");

        freeze = new Freeze(this);
        Actions actions = new Actions(this);
        takeover = new TakeoverManager(this, freeze);
        menus = new MenuManager(this, actions, freeze, takeover);

        Bukkit.getPluginManager().registerEvents(new Listeners(this, menus, freeze, takeover), this);

        getCommand("trollmenu").setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player)) {
                sender.sendMessage(msg("players-only"));
                return true;
            }
            Player p = (Player) sender;
            if (!p.hasPermission("quicktroll.menu")) {
                p.sendMessage(msg("no-permission"));
                return true;
            }
            menus.openMain(p);
            return true;
        });
    }

    @Override
    public void onDisable() {
        takeover.endAll();
        freeze.restoreAll();
    }

    public String msg(String path, String... pairs) {
        String s = getConfig().getString("messages." + path, "&cMissing message: " + path);
        for (int i = 0; i + 1 < pairs.length; i += 2) s = s.replace(pairs[i], pairs[i + 1]);
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
