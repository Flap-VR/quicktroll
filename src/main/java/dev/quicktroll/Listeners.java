package dev.quicktroll;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

public class Listeners implements Listener {

    private final QuickTroll plugin;
    private final MenuManager menus;
    private final Freeze freeze;
    private final TakeoverManager takeover;

    public Listeners(QuickTroll plugin, MenuManager menus, Freeze freeze, TakeoverManager takeover) {
        this.plugin = plugin;
        this.menus = menus;
        this.freeze = freeze;
        this.takeover = takeover;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof MenuManager.Holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player)) return;
        if (e.getClickedInventory() != e.getInventory()) return;
        menus.handleClick((Player) e.getWhoClicked(), (MenuManager.Holder) e.getInventory().getHolder(), e.getRawSlot());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof MenuManager.Holder) e.setCancelled(true);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        freeze.cleanupOnJoin(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        takeover.handleQuit(p);
        freeze.restore(p); // "until they leave and rejoin"
        menus.clear(p);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (!menus.isAwaiting(p)) return;
        e.setCancelled(true);
        e.getRecipients().clear();
        String text = e.getMessage();
        Bukkit.getScheduler().runTask(plugin, () -> menus.receiveAnnouncement(p, text));
    }

    @EventHandler
    public void onInput(PlayerInputEvent e) {
        takeover.setInput(e.getPlayer(), e.getInput());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent e) {
        takeover.handleInteract(e);
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        takeover.handleSwap(e);
    }

    /** Keyboard-disconnected players can't fly/glide around either. */
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!freeze.isFrozen(p) || takeover.isTarget(p)) return;
        Location f = e.getFrom();
        Location t = e.getTo();
        if (t == null) return;
        if ((f.getX() != t.getX() || f.getZ() != t.getZ()) && (p.isFlying() || p.isGliding())) {
            e.setTo(new Location(f.getWorld(), f.getX(), t.getY(), f.getZ(), t.getYaw(), t.getPitch()));
        }
    }
}
