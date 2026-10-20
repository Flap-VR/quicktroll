package dev.quicktroll;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MenuManager {

    public enum Kind {
        JUMPSCARE("quicktroll.jumpscare", "Jumpscare Player", Material.SKELETON_SKULL, "Scare someone with a loud jumpscare.", 11),
        KICK("quicktroll.kick", "Troll Kick", Material.TNT, "Kick someone with a funny reason.", 12),
        KEYBOARD("quicktroll.keyboard", "Disconnect Keyboard", Material.REPEATER, "They can't move until they rejoin.", 13),
        ANNOUNCE("quicktroll.announce", "Announce", Material.BELL, "Send a custom announcement.", 14),
        TAKEOVER("quicktroll.takeover", "Takeover", Material.ENDER_EYE, "Control a player's body for a short time.", 15);

        public final String perm, title, desc;
        public final Material icon;
        public final int slot;

        Kind(String perm, String title, Material icon, String desc, int slot) {
            this.perm = perm;
            this.title = title;
            this.icon = icon;
            this.desc = desc;
            this.slot = slot;
        }
    }

    public static class Holder implements InventoryHolder {
        Inventory inv;
        boolean picker;
        boolean announceMode;
        Kind kind;
        int page;
        final List<UUID> shown = new ArrayList<>();

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final QuickTroll plugin;
    private final Actions actions;
    private final Freeze freeze;
    private final TakeoverManager takeover;
    private final Set<UUID> awaitingChat = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> pendingAnnounce = new ConcurrentHashMap<>();

    public MenuManager(QuickTroll plugin, Actions actions, Freeze freeze, TakeoverManager takeover) {
        this.plugin = plugin;
        this.actions = actions;
        this.freeze = freeze;
        this.takeover = takeover;
    }

    // ---------- building menus ----------

    private ItemStack pane() {
        ItemStack it = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(" ");
        it.setItemMeta(m);
        return it;
    }

    private ItemStack named(Material mat, String name, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(name);
        List<String> l = new ArrayList<>();
        for (String s : lore) l.add(s);
        m.setLore(l);
        it.setItemMeta(m);
        return it;
    }

    public void openMain(Player p) {
        Holder h = new Holder();
        Inventory inv = Bukkit.createInventory(h, 27, "Troll Menu");
        h.inv = inv;
        ItemStack pane = pane();
        for (int i = 0; i < 27; i++) inv.setItem(i, pane);
        for (Kind k : Kind.values()) {
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + k.desc);
            lore.add("");
            if (!p.hasPermission(k.perm)) {
                lore.add(ChatColor.RED + "No permission");
            } else if (k == Kind.TAKEOVER && takeover.canStart(p) != null) {
                lore.add(takeover.canStart(p));
            } else {
                lore.add(ChatColor.YELLOW + "Click to use");
            }
            ItemStack it = named(k.icon, ChatColor.WHITE + "" + ChatColor.BOLD + k.title);
            ItemMeta m = it.getItemMeta();
            m.setLore(lore);
            it.setItemMeta(m);
            inv.setItem(k.slot, it);
        }
        p.openInventory(inv);
    }

    public void openPicker(Player p, Kind kind, int page, boolean announceMode) {
        List<Player> list = new ArrayList<>();
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o.equals(p)) continue;
            if (!announceMode && o.hasPermission("quicktroll.exempt")) continue;
            list.add(o);
        }
        list.sort(Comparator.comparing(pl -> pl.getName().toLowerCase()));
        if (list.isEmpty() && !announceMode) {
            p.sendMessage(plugin.msg("no-players"));
            return;
        }
        int pages = Math.max(1, (list.size() + 44) / 45);
        page = Math.max(0, Math.min(page, pages - 1));

        Holder h = new Holder();
        h.picker = true;
        h.announceMode = announceMode;
        h.kind = kind;
        h.page = page;
        Inventory inv = Bukkit.createInventory(h, 54, "Which Player?");
        h.inv = inv;

        ItemStack pane = pane();
        for (int i = 45; i < 54; i++) inv.setItem(i, pane);

        int start = page * 45;
        for (int i = start; i < Math.min(list.size(), start + 45); i++) {
            Player o = list.get(i);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta sm = (SkullMeta) head.getItemMeta();
            sm.setOwningPlayer(o);
            sm.setDisplayName(ChatColor.YELLOW + o.getName());
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Click to select");
            sm.setLore(lore);
            head.setItemMeta(sm);
            inv.setItem(i - start, head);
            h.shown.add(o.getUniqueId());
        }
        if (page > 0) inv.setItem(45, named(Material.ARROW, ChatColor.WHITE + "Previous Page"));
        if (page < pages - 1) inv.setItem(53, named(Material.ARROW, ChatColor.WHITE + "Next Page"));
        inv.setItem(46, named(Material.BARRIER, ChatColor.RED + "Back"));
        if (announceMode) inv.setItem(49, named(Material.NETHER_STAR, ChatColor.GOLD + "" + ChatColor.BOLD + "Announce To All"));
        p.openInventory(inv);
    }

    // ---------- clicks ----------

    public void handleClick(Player p, Holder h, int slot) {
        if (!h.picker) {
            for (Kind k : Kind.values()) if (k.slot == slot) clickKind(p, k);
            return;
        }
        ItemStack clicked = h.inv.getItem(slot);
        if (slot == 46) {
            openMain(p);
        } else if ((slot == 45 || slot == 53) && clicked != null && clicked.getType() == Material.ARROW) {
            openPicker(p, h.kind, h.page + (slot == 53 ? 1 : -1), h.announceMode);
        } else if (slot == 49 && h.announceMode) {
            sendAnnouncement(p, new ArrayList<>(Bukkit.getOnlinePlayers()));
        } else if (slot >= 0 && slot < 45 && slot < h.shown.size()) {
            Player t = Bukkit.getPlayer(h.shown.get(slot));
            if (t == null) {
                p.sendMessage(plugin.msg("player-offline"));
                return;
            }
            if (h.announceMode) {
                List<Player> one = new ArrayList<>();
                one.add(t);
                sendAnnouncement(p, one);
            } else {
                execute(p, h.kind, t);
            }
        }
    }

    private void clickKind(Player p, Kind k) {
        if (!p.hasPermission(k.perm)) {
            p.sendMessage(plugin.msg("no-permission"));
            return;
        }
        if (k == Kind.ANNOUNCE) {
            p.closeInventory();
            awaitingChat.add(p.getUniqueId());
            p.sendMessage(plugin.msg("announce-prompt"));
            return;
        }
        if (k == Kind.TAKEOVER) {
            String err = takeover.canStart(p);
            if (err != null) {
                p.sendMessage(err);
                return;
            }
        }
        openPicker(p, k, 0, false);
    }

    private void execute(Player admin, Kind k, Player t) {
        if (!admin.hasPermission(k.perm)) {
            admin.sendMessage(plugin.msg("no-permission"));
            return;
        }
        if (t.hasPermission("quicktroll.exempt")) {
            admin.sendMessage(plugin.msg("target-exempt"));
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            admin.closeInventory();
            String name = t.getName();
            switch (k) {
                case JUMPSCARE:
                    actions.jumpscare(t);
                    admin.sendMessage(plugin.msg("jumpscared", "{player}", name));
                    break;
                case KICK:
                    actions.kick(t);
                    admin.sendMessage(plugin.msg("kicked", "{player}", name));
                    break;
                case KEYBOARD:
                    if (freeze.isFrozen(t)) {
                        admin.sendMessage(plugin.msg("keyboard-already", "{player}", name));
                    } else {
                        freeze.freeze(t);
                        admin.sendMessage(plugin.msg("keyboard-done", "{player}", name));
                    }
                    break;
                case TAKEOVER:
                    String err = takeover.start(admin, t);
                    if (err != null) admin.sendMessage(err);
                    break;
                default:
                    break;
            }
        });
    }

    // ---------- announce flow ----------

    public boolean isAwaiting(Player p) {
        return awaitingChat.contains(p.getUniqueId());
    }

    /** Called on the main thread with the admin's next chat line. */
    public void receiveAnnouncement(Player p, String text) {
        awaitingChat.remove(p.getUniqueId());
        if (text.equalsIgnoreCase("cancel")) {
            p.sendMessage(plugin.msg("announce-cancelled"));
            return;
        }
        pendingAnnounce.put(p.getUniqueId(), text);
        openPicker(p, Kind.ANNOUNCE, 0, true);
    }

    private void sendAnnouncement(Player admin, List<Player> to) {
        String text = pendingAnnounce.remove(admin.getUniqueId());
        if (text == null) return;
        actions.announce(admin, text, to);
        admin.closeInventory();
        admin.sendMessage(plugin.msg("announce-sent", "{count}", String.valueOf(to.size())));
    }

    public void clear(Player p) {
        awaitingChat.remove(p.getUniqueId());
        pendingAnnounce.remove(p.getUniqueId());
    }
}
