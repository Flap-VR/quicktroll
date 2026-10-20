package dev.quicktroll;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Stops a player walking/jumping by zeroing walk speed, fly speed and jump strength. No potion icons. */
public class Freeze {
    private final Set<UUID> frozen = new HashSet<>();
    private final Map<UUID, float[]> original = new HashMap<>();
    private final NamespacedKey marker;

    public Freeze(QuickTroll plugin) {
        this.marker = new NamespacedKey(plugin, "frozen");
    }

    public boolean isFrozen(Player p) {
        return frozen.contains(p.getUniqueId());
    }

    public void freeze(Player p) {
        if (!frozen.add(p.getUniqueId())) return;
        original.put(p.getUniqueId(), new float[]{p.getWalkSpeed(), p.getFlySpeed()});
        p.setWalkSpeed(0f);
        p.setFlySpeed(0f);
        AttributeInstance jump = p.getAttribute(Attribute.JUMP_STRENGTH);
        if (jump != null) jump.setBaseValue(0);
        p.getPersistentDataContainer().set(marker, PersistentDataType.BYTE, (byte) 1);
    }

    /** Safe to call on anyone: puts speeds back (to saved values, or vanilla defaults). */
    public void restore(Player p) {
        frozen.remove(p.getUniqueId());
        float[] o = original.remove(p.getUniqueId());
        p.setWalkSpeed(o != null ? o[0] : 0.2f);
        p.setFlySpeed(o != null ? o[1] : 0.1f);
        AttributeInstance jump = p.getAttribute(Attribute.JUMP_STRENGTH);
        if (jump != null) jump.setBaseValue(jump.getDefaultValue());
        p.getPersistentDataContainer().remove(marker);
    }

    /** If the server crashed while someone was frozen, un-freeze them when they join. */
    public void cleanupOnJoin(Player p) {
        if (p.getPersistentDataContainer().has(marker, PersistentDataType.BYTE)) restore(p);
    }

    public void restoreAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (frozen.contains(p.getUniqueId())) restore(p);
        }
    }
}
