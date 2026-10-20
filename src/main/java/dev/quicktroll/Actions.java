package dev.quicktroll;

import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.Collection;

public class Actions {
    private final QuickTroll plugin;

    public Actions(QuickTroll plugin) {
        this.plugin = plugin;
    }

    public void jumpscare(Player t) {
        FileConfiguration c = plugin.getConfig();
        String glyph = ChatColor.WHITE + c.getString("jumpscare.glyph", "\uE000");
        t.sendTitle(glyph, "", 0, c.getInt("jumpscare.duration-ticks", 40), 5);

        String sound = c.getString("jumpscare.sound", "quicktroll:jumpscare");
        String fallback = c.getString("jumpscare.fallback-sound", "entity.warden.sonic_boom");
        int repeat = Math.max(1, c.getInt("jumpscare.repeat", 4));
        for (int i = 0; i < repeat; i++) {
            if (sound != null && !sound.isEmpty())
                t.playSound(t.getLocation(), sound, SoundCategory.MASTER, 1f, 1f);
            if (fallback != null && !fallback.isEmpty())
                t.playSound(t.getLocation(), fallback, SoundCategory.MASTER, 1f, 1f);
        }
    }

    public void kick(Player t) {
        t.kickPlayer(plugin.getConfig().getString("kick.reason", "Lol Bye Bye"));
    }

    public void announce(Player from, String text, Collection<? extends Player> to) {
        FileConfiguration c = plugin.getConfig();
        String fmt = c.getString("announce.format", "&6&lANNOUNCEMENT &8>> &f{message}");
        String line = ChatColor.translateAlternateColorCodes('&',
                fmt.replace("{message}", text).replace("{player}", from.getName()));
        boolean title = c.getBoolean("announce.title", true);
        for (Player p : to) {
            p.sendMessage(line);
            if (title) p.sendTitle(ChatColor.GOLD + "Announcement", ChatColor.WHITE + text, 10, 70, 20);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
        }
    }
}
