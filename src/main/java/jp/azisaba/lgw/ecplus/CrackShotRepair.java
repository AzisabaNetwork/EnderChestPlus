package jp.azisaba.lgw.ecplus;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Container;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Gives CrackShot weapons from 1.15.2 the {@code crackshot:weapon_id} tag that Azisaba's CrackShot checks first.
 * <p>
 * Those weapons carry no tag: CrackShot recognises them only when the part of the display name before "«" equals
 * the configured {@code Item_Name} code for code. A 1.15.2 name converted to a text component and back can come out
 * with different (but equivalent) color codes, so the weapon stops working as a gun. The weapon is found again by
 * its name without color codes, and only when exactly one weapon has that name and the item's material matches
 * its {@code Item_Type}.
 */
final class CrackShotRepair {
    @SuppressWarnings("deprecation")
    private static final NamespacedKey WEAPON_ID = new NamespacedKey("crackshot", "weapon_id");

    private static volatile Map<?, ?> cachedSource;
    private static volatile int cachedSize = -1;
    private static volatile Map<String, String> cachedIndex = Map.of();

    private CrackShotRepair() {}

    /**
     * Tags every untagged CrackShot weapon of the inventory, including those inside shulker boxes.
     *
     * @return the number of tagged weapons (0 when CrackShot is not enabled)
     */
    static int repairInventory(Inventory inventory, Consumer<String> log) {
        Plugin crackShot = Bukkit.getPluginManager().getPlugin("CrackShot");
        if (crackShot == null || !crackShot.isEnabled()) return 0;
        Map<String, String> index = index(crackShot);
        if (index.isEmpty()) return 0;
        Set<String> weapons = new HashSet<>(index.values());
        int repaired = 0;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir() || !item.hasItemMeta()) continue;
            if (item.getItemMeta() instanceof BlockStateMeta meta && meta.hasBlockState()
                    && meta.getBlockState() instanceof Container container) {
                int nested = repairInventory(container.getInventory(), log);
                if (nested > 0) {
                    meta.setBlockState(container);
                    item.setItemMeta(meta);
                    inventory.setItem(slot, item);
                    repaired += nested;
                }
                continue;
            }
            if (tag(crackShot, item, index, weapons, log)) {
                inventory.setItem(slot, item);
                repaired++;
            }
        }
        return repaired;
    }

    @SuppressWarnings("deprecation")
    private static boolean tag(Plugin crackShot, ItemStack item, Map<String, String> index, Set<String> weapons,
                               Consumer<String> log) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String current = pdc.get(WEAPON_ID, PersistentDataType.STRING);
        if (current != null && weapons.contains(current)) return false; // already a working tag
        String parent = index.get(pureName(meta.getDisplayName()));
        if (parent == null || !materialMatches(crackShot, item.getType(), parent)) return false;
        pdc.set(WEAPON_ID, PersistentDataType.STRING, parent);
        item.setItemMeta(meta);
        log.accept("CrackShot weapon '" + parent + "' (" + ChatColor.stripColor(meta.getDisplayName()) + ") tagged");
        return true;
    }

    /** CrackShot's weapon names without color codes -> weapon (ambiguous names are left out). */
    private static Map<String, String> index(Plugin crackShot) {
        Map<?, ?> parents;
        try {
            Object data = crackShot.getClass().getField("data").get(crackShot);
            parents = (Map<?, ?>) data.getClass().getField("parentlist").get(data);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return Map.of();
        }
        if (parents == null) return Map.of();
        if (parents == cachedSource && parents.size() == cachedSize) return cachedIndex;
        Map<String, String> byName = new HashMap<>();
        Set<String> ambiguous = new HashSet<>();
        for (Map.Entry<?, ?> entry : parents.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            String key = pureName(entry.getKey().toString());
            if (key.isEmpty()) continue;
            String id = entry.getValue().toString();
            String previous = byName.putIfAbsent(key, id);
            if (previous != null && !previous.equals(id)) ambiguous.add(key);
        }
        ambiguous.forEach(byName::remove);
        cachedIndex = Map.copyOf(byName);
        cachedSize = parents.size();
        cachedSource = parents;
        return cachedIndex;
    }

    /** The weapon part of a CrackShot item name ("«ammo»" and attachments removed) without color codes. */
    @SuppressWarnings("deprecation")
    static String pureName(String displayName) {
        String name = displayName;
        int ammo = name.lastIndexOf('«');
        if (ammo >= 0) name = name.substring(0, ammo);
        int attachment = name.indexOf('▶');
        if (attachment >= 0) name = name.substring(0, attachment);
        String stripped = ChatColor.stripColor(name);
        return (stripped == null ? name : stripped).trim();
    }

    private static boolean materialMatches(Plugin crackShot, Material material, String parent) {
        String type;
        try {
            Object data = crackShot.getClass().getField("data").get(crackShot);
            type = (String) data.getClass().getMethod("getString", String.class)
                    .invoke(data, parent + ".Item_Information.Item_Type");
        } catch (ReflectiveOperationException | RuntimeException e) {
            return true;
        }
        if (type == null) return true;
        String name = type.split("~", 2)[0].split(":", 2)[0].trim();
        if (name.isEmpty() || name.chars().allMatch(Character::isDigit)) return true; // legacy numeric id
        Material configured = Material.matchMaterial(name);
        return configured == null || configured == material;
    }
}
