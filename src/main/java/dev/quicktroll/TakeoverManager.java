package dev.quicktroll;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Input;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Takeover: the admin rides the target (invisible to everyone), so the admin is stuck in place.
 * The admin's WASD/jump/sprint + camera direction push the target around with velocity.
 * The target can't walk or jump on their own (see Freeze).
 */
public class TakeoverManager {

    private static class Session {
        UUID admin, target;
        long endAt;
        Input input;
        BukkitTask task;
        double lastY;
        int tick;
        boolean alreadyFrozen;
    }

    private final QuickTroll plugin;
    private final Freeze freeze;
    private final Map<UUID, Session> byAdmin = new HashMap<>();
    private final Map<UUID, Session> byTarget = new HashMap<>();
    private final Map<UUID, Long> cooldownUntil = new HashMap<>();

    public TakeoverManager(QuickTroll plugin, Freeze freeze) {
        this.plugin = plugin;
        this.freeze = freeze;
    }

    public boolean isTarget(Player p) {
        return byTarget.containsKey(p.getUniqueId());
    }

    public boolean isAdmin(Player p) {
        return byAdmin.containsKey(p.getUniqueId());
    }

    /** Returns an error message, or null if this admin can start a takeover. */
    public String canStart(Player admin) {
        if (!admin.hasPermission("quicktroll.takeover")) return plugin.msg("no-permission");
        if (byAdmin.containsKey(admin.getUniqueId())) return plugin.msg("takeover-busy");
        Long until = cooldownUntil.get(admin.getUniqueId());
        if (until != null) {
            long left = (until - System.currentTimeMillis() + 999) / 1000;
            if (left > 0) return plugin.msg("takeover-cooldown", "{seconds}", String.valueOf(left));
        }
        return null;
    }

    /** Returns an error message, or null on success. */
    public String start(Player admin, Player target) {
        String err = canStart(admin);
        if (err != null) return err;
        if (admin.equals(target) || target.isDead() || admin.isDead()) return plugin.msg("takeover-failed");
        if (target.hasPermission("quicktroll.exempt")) return plugin.msg("target-exempt");
        if (byTarget.containsKey(target.getUniqueId()) || byAdmin.containsKey(target.getUniqueId())
                || byTarget.containsKey(admin.getUniqueId())
                || target.isInsideVehicle() || admin.isInsideVehicle() || !target.getPassengers().isEmpty()) {
            return plugin.msg("takeover-target-busy");
        }

        Session s = new Session();
        s.admin = admin.getUniqueId();
        s.target = target.getUniqueId();
        s.endAt = System.currentTimeMillis() + plugin.getConfig().getInt("takeover.duration-seconds", 30) * 1000L;
        s.alreadyFrozen = freeze.isFrozen(target);

        freeze.freeze(target);
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (!o.equals(admin)) o.hidePlayer(plugin, admin);
        }
        if (!target.addPassenger(admin)) {
            for (Player o : Bukkit.getOnlinePlayers()) o.showPlayer(plugin, admin);
            if (!s.alreadyFrozen) freeze.restore(target);
            return plugin.msg("takeover-failed");
        }

        s.lastY = target.getLocation().getY();
        byAdmin.put(s.admin, s);
        byTarget.put(s.target, s);
        s.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(s), 1L, 1L);
        admin.sendMessage(plugin.msg("takeover-start", "{player}", target.getName()));
        return null;
    }

    private void tick(Session s) {
        Player admin = Bukkit.getPlayer(s.admin);
        Player target = Bukkit.getPlayer(s.target);
        if (admin == null || target == null) {
            end(s, plugin.msg("takeover-target-left"));
            return;
        }
        if (target.isDead()) {
            end(s, plugin.msg("takeover-target-died"));
            return;
        }
        if (System.currentTimeMillis() >= s.endAt) {
            end(s, plugin.msg("takeover-timeup"));
            return;
        }
        if (admin.isDead() || admin.getVehicle() == null || !admin.getVehicle().equals(target)) {
            end(s, plugin.msg("takeover-exited"));
            return;
        }
        move(s, admin, target);
        if (s.tick++ % 10 == 0) {
            long left = Math.max(0, (s.endAt - System.currentTimeMillis() + 999) / 1000);
            admin.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(
                    plugin.msg("takeover-actionbar", "{player}", target.getName(), "{seconds}", String.valueOf(left))));
        }
    }

    private void move(Session s, Player admin, Player target) {
        double fwd = 0, str = 0;
        boolean jump = false, sprint = false;
        Input in = s.input;
        if (in != null) {
            fwd = (in.isForward() ? 1 : 0) - (in.isBackward() ? 1 : 0);
            str = (in.isLeft() ? 1 : 0) - (in.isRight() ? 1 : 0);
            jump = in.isJump();
            sprint = in.isSprint();
        }
        double yaw = Math.toRadians(admin.getLocation().getYaw());
        double dx = -Math.sin(yaw) * fwd + Math.cos(yaw) * str;
        double dz = Math.cos(yaw) * fwd + Math.sin(yaw) * str;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len > 1) {
            dx /= len;
            dz /= len;
        }
        double speed = sprint ? 0.28 : 0.215;
        double y = target.getLocation().getY();
        double vy = target.isOnGround() ? (jump ? 0.42 : 0.0) : (y - s.lastY);
        s.lastY = y;
        target.setVelocity(new Vector(dx * speed, vy, dz * speed));
    }

    public void setInput(Player admin, Input input) {
        Session s = byAdmin.get(admin.getUniqueId());
        if (s != null) s.input = input;
    }

    public void handleInteract(PlayerInteractEvent e) {
        Session s = byAdmin.get(e.getPlayer().getUniqueId());
        if (s == null) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player target = Bukkit.getPlayer(s.target);
        if (target == null) return;
        Player admin = e.getPlayer();
        Action a = e.getAction();
        if (a == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null
                && e.getClickedBlock().getType() == Material.ENDER_CHEST) {
            admin.openInventory(target.getEnderChest());
        } else if (a == Action.RIGHT_CLICK_BLOCK || a == Action.RIGHT_CLICK_AIR) {
            admin.openInventory(target.getInventory());
        }
    }

    public void handleSwap(PlayerSwapHandItemsEvent e) {
        Session s = byAdmin.get(e.getPlayer().getUniqueId());
        if (s == null) return;
        e.setCancelled(true);
        Player target = Bukkit.getPlayer(s.target);
        if (target != null && !target.isDead()) {
            e.getPlayer().sendMessage(plugin.msg("takeover-killed"));
            target.setHealth(0.0);
        }
    }

    public void handleQuit(Player p) {
        Session s = byAdmin.get(p.getUniqueId());
        if (s == null) s = byTarget.get(p.getUniqueId());
        if (s != null) end(s, plugin.msg("takeover-target-left"));
    }

    private void end(Session s, String reason) {
        if (s.task != null) s.task.cancel();
        byAdmin.remove(s.admin);
        byTarget.remove(s.target);
        cooldownUntil.put(s.admin, System.currentTimeMillis()
                + plugin.getConfig().getInt("takeover.cooldown-seconds", 60) * 1000L);

        Player admin = Bukkit.getPlayer(s.admin);
        Player target = Bukkit.getPlayer(s.target);
        if (target != null && !s.alreadyFrozen) freeze.restore(target);
        if (admin != null) {
            admin.closeInventory();
            admin.leaveVehicle();
            for (Player o : Bukkit.getOnlinePlayers()) o.showPlayer(plugin, admin);
            admin.sendMessage(reason);
        }
    }

    public void endAll() {
        for (Session s : new ArrayList<>(byAdmin.values())) end(s, plugin.msg("takeover-exited"));
    }
}
