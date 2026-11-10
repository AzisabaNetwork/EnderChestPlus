package jp.azisaba.lgw.ecplus;

import jp.azisaba.lgw.ecplus.utils.nbt.Nbt;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Container;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Restores the NBT of 1.15.2 StorageBox items that lost it when they were first moved into the database.
 * <p>
 * Without {@code storageBoxType} the StorageBox plugin falls back to the item's lore, whose
 * "不透明: true (STICK)" line names the box's own material, so every opaque box turned into a box of
 * sticks. The original tags are still in the legacy YAML; they are found by the box's {@code randomUUID}
 * (the "ID:" lore line), so the box may have moved to another page or slot since.
 * The box's current amount is kept: it reflects what the player has taken out or put in since.
 */
final class LegacyStorageBoxRepair {
    private static final String PDC = "PublicBukkitValues";
    private static final String PDC_TYPE = "storagebox:type";
    private static final String PDC_COMPONENT = "storagebox:component_data";
    private static final List<String> COPIED_TAGS = List.of(
            "storageBoxTag", "storageBoxAmount", "storageBoxAutoCollect", "storageBoxAutoBuy", "randomUUID");
    /** 1.15.2 material names that no longer exist on 1.21.11. */
    private static final Map<String, String> RENAMED = Map.of(
            "GRASS", "SHORT_GRASS",
            "GRASS_PATH", "DIRT_PATH",
            "SCUTE", "TURTLE_SCUTE",
            "CHAIN", "IRON_CHAIN");

    private LegacyStorageBoxRepair() {}

    /** The 1.21.11 name of a material saved by 1.15.2, or the name unchanged when unknown. */
    static String modernMaterialName(String legacyName) {
        if (legacyName == null) return null;
        String upper = legacyName.trim().toUpperCase(Locale.ROOT);
        if (upper.isEmpty() || upper.equals("NULL")) return upper;
        if (Material.getMaterial(upper) != null) return upper;
        String renamed = RENAMED.get(upper);
        if (renamed != null && Material.getMaterial(renamed) != null) return renamed;
        Material matched = Material.matchMaterial(upper);
        return matched != null ? matched.name() : upper;
    }

    /** Cheap check, without NBT parsing, for items that may need {@link #repair}. */
    static boolean mayNeedRepair(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        if (loreId(item) != null) return isSuspiciousBox(item);
        if (!(item.getItemMeta() instanceof BlockStateMeta meta) || !meta.hasBlockState()
                || !(meta.getBlockState() instanceof Container container)) return false;
        for (ItemStack nested : container.getInventory().getContents()) {
            if (loreId(nested) != null && isSuspiciousBox(nested)) return true;
        }
        return false;
    }

    /** True if the box's stored type is missing, STICK or unknown (only then is the legacy YAML read). */
    private static boolean isSuspiciousBox(ItemStack item) {
        try {
            Nbt.CompoundTag components = Nbt.readCompressed(item.serializeAsBytes()).getCompound("components");
            Nbt.CompoundTag customData = components == null ? null : components.getCompound("minecraft:custom_data");
            if (customData == null) return true;
            Nbt.CompoundTag pdc = customData.getCompound(PDC);
            String pdcType = pdc == null ? null : pdc.getString(PDC_TYPE);
            String current = pdcType != null ? pdcType : customData.getString("storageBoxType");
            return needsRepair(current, "");
        } catch (Exception e) {
            return false;
        }
    }

    /** Legacy StorageBox root tags of the legacy YAML (top level and inside shulker boxes), by randomUUID. */
    static Map<String, Nbt.CompoundTag> indexLegacyBoxes(Map<?, ?> raw) {
        Map<String, Nbt.CompoundTag> boxes = new HashMap<>();
        for (Object page : raw.values()) {
            if (!(page instanceof Map<?, ?> slots)) continue;
            for (Object slot : slots.values()) {
                if (!(slot instanceof Map<?, ?> item) || !(item.get("meta") instanceof Map<?, ?> meta)) continue;
                Object internal = meta.get("internal");
                if (internal == null) continue;
                try {
                    Nbt.CompoundTag root = Nbt.readCompressed(Base64.getDecoder().decode(internal.toString()));
                    addBox(boxes, root);
                    Nbt.CompoundTag blockEntity = root.getCompound("BlockEntityTag");
                    Nbt.ListTag items = blockEntity == null ? null : blockEntity.getList("Items");
                    if (items == null) continue;
                    for (Nbt.Tag nested : items.getElements()) {
                        if (nested instanceof Nbt.CompoundTag nestedItem) addBox(boxes, nestedItem.getCompound("tag"));
                    }
                } catch (Exception ignored) {
                    // not decodable: nothing to restore from
                }
            }
        }
        return boxes;
    }

    private static void addBox(Map<String, Nbt.CompoundTag> boxes, Nbt.CompoundTag tag) {
        if (tag == null) return;
        String type = tag.getString("storageBoxType");
        String id = tag.getString("randomUUID");
        if (type != null && id != null && !id.isBlank()) boxes.put(id.trim(), tag);
    }

    /**
     * Repairs every StorageBox in the inventory, including those inside shulker boxes.
     *
     * @return the number of repaired boxes
     */
    static int repairInventory(Inventory inventory, Map<String, Nbt.CompoundTag> legacy, Consumer<String> log) {
        int repaired = 0;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (!mayNeedRepair(item)) continue;
            ItemStack fixed = repair(item, legacy, log);
            if (fixed != null) {
                inventory.setItem(slot, fixed);
                repaired++;
                continue;
            }
            if (item.getItemMeta() instanceof BlockStateMeta meta && meta.hasBlockState()
                    && meta.getBlockState() instanceof Container container) {
                int nested = repairInventory(container.getInventory(), legacy, log);
                if (nested > 0) {
                    meta.setBlockState(container);
                    item.setItemMeta(meta);
                    inventory.setItem(slot, item);
                    repaired += nested;
                }
            }
        }
        return repaired;
    }

    /** The repaired copy of a StorageBox that lost its type, or null if it needs nothing or cannot be repaired. */
    static ItemStack repair(ItemStack item, Map<String, Nbt.CompoundTag> legacy, Consumer<String> log) {
        String id = loreId(item);
        if (id == null) return null;
        Nbt.CompoundTag legacyTag = legacy.get(id);
        if (legacyTag == null) return null;
        String legacyType = modernMaterialName(legacyTag.getString("storageBoxType"));
        if (legacyType == null || legacyType.isEmpty() || legacyType.equals("NULL")) return null;

        try {
            Nbt.CompoundTag root = Nbt.readCompressed(item.serializeAsBytes());
            Nbt.CompoundTag components = root.getCompound("components");
            if (components == null) {
                components = new Nbt.CompoundTag();
                root.put("components", components);
            }
            Nbt.CompoundTag customData = components.getCompound("minecraft:custom_data");
            if (customData == null) {
                customData = new Nbt.CompoundTag();
                components.put("minecraft:custom_data", customData);
            }
            Nbt.CompoundTag pdc = customData.getCompound(PDC);
            String pdcType = pdc == null ? null : pdc.getString(PDC_TYPE);
            String current = pdcType != null ? pdcType : customData.getString("storageBoxType");
            if (!needsRepair(current, legacyType)) return null;

            customData.putString("storageBoxType", legacyType);
            for (String key : COPIED_TAGS) {
                Nbt.Tag value = legacyTag.get(key);
                if (value != null && customData.get(key) == null) customData.put(key, value.copy());
            }
            if (pdcType != null) {
                // StorageBox reads its PDC first; the cached component would still be a stick
                pdc.putString(PDC_TYPE, legacyType);
                pdc.remove(PDC_COMPONENT);
            }
            ItemStack repaired = ItemStack.deserializeBytes(Nbt.writeCompressed(root));
            if (repaired == null || repaired.getType().isAir()) return null;
            log.accept("StorageBox " + id + ": " + (current == null ? "(なし)" : current) + " -> " + legacyType
                    + " (旧データの数: " + legacyTag.getNumber("storageBoxAmount") + ")");
            return repaired;
        } catch (Exception e) {
            log.accept("StorageBox " + id + " の修復に失敗しました: " + e.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------------ manual restore (/ecp storagebox)

    /** Why {@link #restore} replaces a box. */
    enum Reason {
        LOST_NBT("NBT消失"),
        LOST_CONTENT("中身のNBT消失"),
        DUPLICATE("重複ID上書き");

        final String label;

        Reason(String label) {
            this.label = label;
        }
    }

    /** Counters and report lines of one manual restore. */
    static final class RestoreReport {
        final java.util.Map<Reason, Integer> restored = new java.util.EnumMap<>(Reason.class);
        final java.util.List<String> lines = new java.util.ArrayList<>();
        final java.util.Set<String> seen = new java.util.HashSet<>();
        int healthy;
        int duplicateMismatch;
        int failed;

        int total() {
            return restored.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    /** randomUUIDs ("ID:" lore) that appear more than once in the inventories, inside shulker boxes included. */
    static java.util.Set<String> duplicateIds(Iterable<Inventory> inventories) {
        Map<String, Integer> counts = new HashMap<>();
        for (Inventory inventory : inventories) countIds(inventory.getContents(), counts);
        java.util.Set<String> duplicates = new java.util.HashSet<>();
        counts.forEach((id, count) -> {
            if (count > 1) duplicates.add(id);
        });
        return duplicates;
    }

    private static void countIds(ItemStack[] items, Map<String, Integer> counts) {
        for (ItemStack item : items) {
            String id = loreId(item);
            if (id != null) {
                counts.merge(id, 1, Integer::sum);
            } else if (item != null && item.getItemMeta() instanceof BlockStateMeta meta && meta.hasBlockState()
                    && meta.getBlockState() instanceof Container container) {
                countIds(container.getInventory().getContents(), counts);
            }
        }
    }

    /**
     * Restores the boxes of the inventory from the legacy YAML: boxes that lost their NBT, boxes whose content
     * item lost its NBT (recovered from the lore as a plain item), and boxes whose ID is in {@code duplicates}.
     * The amount and auto collect/buy settings of each box are kept. Changes the inventory only when applying.
     *
     * @return the number of boxes restored (or that would be restored)
     */
    static int restoreInventory(Inventory inventory, Map<String, Nbt.CompoundTag> legacy, java.util.Set<String> duplicates,
                                boolean apply, String where, RestoreReport report) {
        int restored = 0;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) continue;
            String location = where + " スロット" + slot;
            if (loreId(item) != null) {
                ItemStack fixed = restore(item, legacy, duplicates, location, report);
                if (fixed != null) {
                    restored++;
                    if (apply) inventory.setItem(slot, fixed);
                }
                continue;
            }
            if (item.getItemMeta() instanceof BlockStateMeta meta && meta.hasBlockState()
                    && meta.getBlockState() instanceof Container container) {
                int nested = restoreInventory(container.getInventory(), legacy, duplicates, apply,
                        location + " " + item.getType() + "内", report);
                if (nested > 0 && apply) {
                    meta.setBlockState(container);
                    item.setItemMeta(meta);
                    inventory.setItem(slot, item);
                }
                restored += nested;
            }
        }
        return restored;
    }

    /** The restored copy of one box, or null when it needs nothing or cannot be restored. */
    static ItemStack restore(ItemStack item, Map<String, Nbt.CompoundTag> legacy, java.util.Set<String> duplicates,
                             String where, RestoreReport report) {
        String id = loreId(item);
        if (id == null) return null;
        report.seen.add(id);
        Nbt.CompoundTag legacyTag = legacy.get(id);
        if (legacyTag == null) return null;
        String legacyType = modernMaterialName(legacyTag.getString("storageBoxType"));
        if (legacyType == null || legacyType.isEmpty() || legacyType.equals("NULL")) return null;

        try {
            Nbt.CompoundTag root = Nbt.readCompressed(item.serializeAsBytes());
            Nbt.CompoundTag components = root.getCompound("components");
            if (components == null) {
                components = new Nbt.CompoundTag();
                root.put("components", components);
            }
            Nbt.CompoundTag customData = components.getCompound("minecraft:custom_data");
            if (customData == null) {
                customData = new Nbt.CompoundTag();
                components.put("minecraft:custom_data", customData);
            }
            Nbt.CompoundTag pdc = customData.getCompound(PDC);
            String pdcType = pdc == null ? null : pdc.getString(PDC_TYPE);
            String current = pdcType != null ? pdcType : customData.getString("storageBoxType");
            Nbt.CompoundTag content = customData.getCompound("storageBoxTag");
            Nbt.CompoundTag legacyContent = legacyTag.getCompound("storageBoxTag");
            boolean hasContent = content != null && !content.getTags().isEmpty();
            boolean legacyHasContent = legacyContent != null && !legacyContent.getTags().isEmpty();
            boolean sameType = current != null && modernMaterialName(current).equals(legacyType);
            String component = pdc == null ? null : pdc.getString(PDC_COMPONENT);

            Reason reason;
            if (needsRepair(current, legacyType)) {
                reason = Reason.LOST_NBT;
            } else if (!hasContent && legacyHasContent && sameType && !isCustomizedContent(component)) {
                reason = Reason.LOST_CONTENT;
            } else if (duplicates.contains(id) && sameType) {
                reason = Reason.DUPLICATE;
            } else {
                if (duplicates.contains(id)) {
                    report.duplicateMismatch++;
                    report.lines.add(where + ": 重複ID " + id + " ですが種類が旧データ (" + legacyType + ") と違う ("
                            + current + ") ため上書きしません");
                } else {
                    report.healthy++;
                }
                return null;
            }

            customData.putString("storageBoxType", legacyType);
            if (legacyHasContent) {
                customData.put("storageBoxTag", legacyContent.copy());
            } else {
                customData.remove("storageBoxTag");
            }
            for (String key : COPIED_TAGS) {
                Nbt.Tag value = legacyTag.get(key);
                if (value != null && customData.get(key) == null) customData.put(key, value.copy());
            }
            if (pdc != null) {
                // StorageBox reads its PDC first; the cached content would still be the old item
                pdc.putString(PDC_TYPE, legacyType);
                pdc.remove(PDC_COMPONENT);
            }
            ItemStack restored = ItemStack.deserializeBytes(Nbt.writeCompressed(root));
            if (restored == null || restored.getType().isAir()) {
                report.failed++;
                report.lines.add(where + ": " + id + " の復元アイテムを作れませんでした");
                return null;
            }
            report.restored.merge(reason, 1, Integer::sum);
            report.lines.add(where + ": " + id + " を復元 [" + reason.label + "] " + (current == null ? "(なし)" : current)
                    + " -> " + legacyType + " (旧データの数: " + legacyTag.getNumber("storageBoxAmount") + ")");
            return restored;
        } catch (Exception e) {
            report.failed++;
            report.lines.add(where + ": " + id + " の復元に失敗しました: " + e.getMessage());
            return null;
        }
    }

    /** True when the box's current content item has its own name or lore (the player set it again since). */
    private static boolean isCustomizedContent(String serialized) {
        if (serialized == null || serialized.isBlank()) return false;
        try {
            org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
            yaml.loadFromString(serialized);
            ItemStack content = yaml.getItemStack("item");
            ItemMeta meta = content == null ? null : content.getItemMeta();
            return meta != null && (meta.hasDisplayName() || meta.hasLore());
        } catch (Exception e) {
            return true; // unknown: do not replace it
        }
    }

    private static boolean needsRepair(String current, String legacyType) {
        if (current == null || current.isBlank()) return true;
        String upper = current.trim().toUpperCase(Locale.ROOT);
        // lost NBT -> lore fallback read "不透明: true (STICK)" as the content
        if (upper.equals("STICK") && !legacyType.equals("STICK")) return true;
        // a 1.15.2 name that 1.21.11 no longer knows
        return !upper.equals("NULL") && Material.getMaterial(upper) == null;
    }

    /** The randomUUID from the "ID: ..." lore line of an item named "Storage Box ...", or null. */
    @SuppressWarnings("deprecation")
    static String loreId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName() || !meta.hasLore()) return null;
        String name = ChatColor.stripColor(meta.getDisplayName());
        if (name == null || !name.startsWith("Storage Box")) return null;
        List<String> lore = meta.getLore();
        if (lore == null) return null;
        for (String line : lore) {
            String stripped = ChatColor.stripColor(line);
            if (stripped == null) continue;
            stripped = stripped.trim();
            if (stripped.startsWith("ID:")) {
                String id = stripped.substring(3).trim();
                return id.isEmpty() ? null : id;
            }
        }
        return null;
    }
}
