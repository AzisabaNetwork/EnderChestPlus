package jp.azisaba.lgw.ecplus;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.BundleContents;
import io.papermc.paper.datacomponent.item.ItemContainerContents;
import jp.azisaba.lgw.ecplus.utils.nbt.Nbt;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import io.papermc.paper.persistence.PersistentDataContainerView;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Restores the PublicBukkitValues (PDC) of items imported from 1.15.2 YAML.
 * <p>
 * {@link InventoryData#sanitizeLegacyYaml} wraps every PDC value in double quotes so that Paper 1.21's
 * CraftNBTTagConfigSerializer does not crash on empty values. Paper keeps those quotes, so every value
 * became a string with literal quotes: {@code mythicmobs:type: SWORD} turned into the string {@code "SWORD"},
 * and typed values lost their type ({@code minecraft:build_tool_item: 1i}, the BuildTool marker, turned
 * into the string {@code "1i"} instead of the int 1, so BuildTool no longer recognised its own item).
 * <p>
 * This unwraps such values and reads them the way 1.15.2's CraftNBTTagConfigSerializer did.
 */
final class LegacyPdcRepair {
    private static final String PDC = "PublicBukkitValues";

    // the patterns of 1.15.2's CraftNBTTagConfigSerializer and MojangsonParser (case-insensitive like them)
    private static final Pattern INT = Pattern.compile("[-+]?(?:0|[1-9][0-9]*)?i", Pattern.CASE_INSENSITIVE);
    private static final Pattern DOUBLE = Pattern.compile("[-+]?(?:[0-9]+[.]?|[0-9]*[.][0-9]+)(?:e[-+]?[0-9]+)?d", Pattern.CASE_INSENSITIVE);
    private static final Pattern FLOAT = Pattern.compile("[-+]?(?:[0-9]+[.]?|[0-9]*[.][0-9]+)(?:e[-+]?[0-9]+)?f", Pattern.CASE_INSENSITIVE);
    private static final Pattern BYTE = Pattern.compile("[-+]?(?:0|[1-9][0-9]*)b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHORT = Pattern.compile("[-+]?(?:0|[1-9][0-9]*)s", Pattern.CASE_INSENSITIVE);
    private static final Pattern LONG = Pattern.compile("[-+]?(?:0|[1-9][0-9]*)l", Pattern.CASE_INSENSITIVE);

    private LegacyPdcRepair() {}

    /**
     * Reads a PDC value the way 1.15.2 did: "1i" is an int, "1b"/"1s"/"1L"/"1.5f"/"1.5d" are typed numbers,
     * true/false are bytes, anything else (including plain numbers, which 1.15.2 turned back into strings) is a string.
     */
    static Nbt.Tag legacyValue(String value) {
        try {
            if (INT.matcher(value).matches() && value.length() > 1 && Character.isDigit(value.charAt(value.length() - 2))) {
                return new Nbt.IntTag(Integer.parseInt(strip(value)));
            }
            if (DOUBLE.matcher(value).matches()) return new Nbt.DoubleTag(Double.parseDouble(strip(value)));
            if (FLOAT.matcher(value).matches()) return new Nbt.FloatTag(Float.parseFloat(strip(value)));
            if (BYTE.matcher(value).matches()) return new Nbt.ByteTag(Byte.parseByte(strip(value)));
            if (SHORT.matcher(value).matches()) return new Nbt.ShortTag(Short.parseShort(strip(value)));
            if (LONG.matcher(value).matches()) return new Nbt.LongTag(Long.parseLong(strip(value)));
        } catch (NumberFormatException ignored) {
            // out of range: 1.15.2 kept it as a string as well
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.equals("true")) return new Nbt.ByteTag((byte) 1);
        if (lower.equals("false")) return new Nbt.ByteTag((byte) 0);
        return new Nbt.StringTag(value);
    }

    private static String strip(String value) {
        String number = value.substring(0, value.length() - 1);
        return number.startsWith("+") ? number.substring(1) : number;
    }

    static boolean isWrapped(String value) {
        return value.length() >= 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"';
    }

    /** Unwraps the quoted string values of a PublicBukkitValues compound (and of nested containers). */
    static boolean repairPdc(Nbt.CompoundTag pdc) {
        boolean changed = false;
        for (Map.Entry<String, Nbt.Tag> entry : pdc.getTags().entrySet()) {
            Nbt.Tag value = entry.getValue();
            if (value instanceof Nbt.StringTag string && isWrapped(string.getValue())) {
                String inner = string.getValue();
                entry.setValue(legacyValue(inner.substring(1, inner.length() - 1)));
                changed = true;
            } else if (value instanceof Nbt.CompoundTag compound) {
                changed |= repairPdc(compound);
            } else if (value instanceof Nbt.ListTag list) {
                for (Nbt.Tag element : list.getElements()) {
                    if (element instanceof Nbt.CompoundTag compound) changed |= repairPdc(compound);
                }
            }
        }
        return changed;
    }

    /** Repairs every PublicBukkitValues compound in the tree (the item's own and those of nested items). */
    static boolean repairTree(Nbt.Tag tag) {
        boolean changed = false;
        if (tag instanceof Nbt.CompoundTag compound) {
            for (Map.Entry<String, Nbt.Tag> entry : compound.getTags().entrySet()) {
                if (entry.getKey().equals(PDC) && entry.getValue() instanceof Nbt.CompoundTag pdc) {
                    changed |= repairPdc(pdc);
                } else {
                    changed |= repairTree(entry.getValue());
                }
            }
        } else if (tag instanceof Nbt.ListTag list) {
            for (Nbt.Tag element : list.getElements()) changed |= repairTree(element);
        }
        return changed;
    }

    /**
     * Converts the PublicBukkitValues map of a raw (sanitized) legacy YAML item into NBT, for items that
     * Bukkit could not load and that are rebuilt from their raw NBT instead.
     */
    static Nbt.Tag fromRawYaml(Object value) {
        if (value instanceof Map<?, ?> map) {
            Nbt.CompoundTag compound = new Nbt.CompoundTag();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Nbt.Tag tag = fromRawYaml(entry.getValue());
                if (tag != null) compound.put(String.valueOf(entry.getKey()), tag);
            }
            return compound;
        }
        if (value instanceof List<?> list) {
            Nbt.ListTag listTag = new Nbt.ListTag();
            for (Object element : list) {
                Nbt.Tag tag = fromRawYaml(element);
                if (tag != null && (listTag.isEmpty() || listTag.getElementTypeId() == tag.getTypeId())) listTag.add(tag);
            }
            return listTag;
        }
        if (value == null) return new Nbt.StringTag("");
        String string = value.toString();
        if (isWrapped(string)) string = string.substring(1, string.length() - 1);
        return legacyValue(string);
    }

    /** Cheap check through the PDC view and data components, without serializing the item. */
    static boolean mayNeedRepair(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        if (hasWrappedString(item.getPersistentDataContainer(), 0)) return true;
        ItemContainerContents container = item.getData(DataComponentTypes.CONTAINER);
        if (container != null) {
            for (ItemStack nested : container.contents()) if (mayNeedRepair(nested)) return true;
        }
        BundleContents bundle = item.getData(DataComponentTypes.BUNDLE_CONTENTS);
        if (bundle != null) {
            for (ItemStack nested : bundle.contents()) if (mayNeedRepair(nested)) return true;
        }
        return false;
    }

    private static boolean hasWrappedString(PersistentDataContainerView pdc, int depth) {
        if (pdc.isEmpty() || depth > 8) return false;
        for (NamespacedKey key : pdc.getKeys()) {
            if (pdc.has(key, PersistentDataType.STRING)) {
                String value = pdc.get(key, PersistentDataType.STRING);
                if (value != null && isWrapped(value)) return true;
            } else if (pdc.has(key, PersistentDataType.TAG_CONTAINER)) {
                PersistentDataContainerView nested = pdc.get(key, PersistentDataType.TAG_CONTAINER);
                if (nested != null && hasWrappedString(nested, depth + 1)) return true;
            } else if (pdc.has(key, PersistentDataType.TAG_CONTAINER_ARRAY)) {
                PersistentDataContainerView[] nested = pdc.get(key, PersistentDataType.TAG_CONTAINER_ARRAY);
                if (nested == null) continue;
                for (PersistentDataContainerView element : nested) {
                    if (hasWrappedString(element, depth + 1)) return true;
                }
            }
        }
        return false;
    }

    /** @return the repaired item, or null when nothing had to be changed */
    static ItemStack repair(ItemStack item) {
        if (!mayNeedRepair(item)) return null;
        try {
            Nbt.CompoundTag root = Nbt.readCompressed(item.serializeAsBytes());
            if (!repairTree(root)) return null;
            ItemStack repaired = ItemStack.deserializeBytes(Nbt.writeCompressed(root));
            return repaired == null || repaired.getType().isAir() ? null : repaired;
        } catch (Exception e) {
            MigrationLog.warn("Could not repair legacy PDC of " + item.getType() + ": " + e.getMessage());
            return null;
        }
    }

    /** @return the number of repaired items */
    static int repairInventory(Inventory inventory) {
        int repaired = 0;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack fixed = repair(inventory.getItem(slot));
            if (fixed != null) {
                inventory.setItem(slot, fixed);
                repaired++;
            }
        }
        return repaired;
    }
}
