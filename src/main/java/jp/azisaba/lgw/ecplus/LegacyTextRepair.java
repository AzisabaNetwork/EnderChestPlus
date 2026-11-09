package jp.azisaba.lgw.ecplus;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import jp.azisaba.lgw.ecplus.utils.nbt.Nbt;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 1.15.2 ignored invalid "color" values in JSON text (e.g. {@code "color":"reset\""} in hand-written books),
 * but the modern text codec rejects the whole component, so the DataFixer fails on the item and it is lost.
 * This repairs such values in a legacy item's "tag" before it goes through the DataFixer.
 */
final class LegacyTextRepair {
    /** Colors the modern codec accepts by name ("reset" is a formatting code, not a color, and is rejected). */
    private static final Set<String> NAMED_COLORS = Set.of(
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray",
            "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white");
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private LegacyTextRepair() {
    }

    /**
     * Repairs the JSON text of book pages, display name and lore in a legacy item "tag",
     * including the items of a container's BlockEntityTag.
     *
     * @return the number of repaired text values
     */
    static int repairTag(Nbt.CompoundTag tag) {
        if (tag == null) return 0;
        int repaired = repairStringList(tag.getList("pages"));
        Nbt.CompoundTag display = tag.getCompound("display");
        if (display != null) {
            repaired += repairString(display, "Name");
            repaired += repairStringList(display.getList("Lore"));
        }
        Nbt.CompoundTag blockEntity = tag.getCompound("BlockEntityTag");
        Nbt.ListTag items = blockEntity == null ? null : blockEntity.getList("Items");
        if (items != null) {
            for (Nbt.Tag item : items.getElements()) {
                if (item instanceof Nbt.CompoundTag compound) repaired += repairTag(compound.getCompound("tag"));
            }
        }
        return repaired;
    }

    private static int repairString(Nbt.CompoundTag compound, String key) {
        if (!(compound.get(key) instanceof Nbt.StringTag string)) return 0;
        String fixed = repairJson(string.getValue());
        if (fixed == null) return 0;
        compound.putString(key, fixed);
        return 1;
    }

    private static int repairStringList(Nbt.ListTag list) {
        if (list == null) return 0;
        int repaired = 0;
        java.util.List<Nbt.Tag> elements = list.getElements();
        for (int i = 0; i < elements.size(); i++) {
            if (!(elements.get(i) instanceof Nbt.StringTag string)) continue;
            String fixed = repairJson(string.getValue());
            if (fixed != null) {
                elements.set(i, new Nbt.StringTag(fixed));
                repaired++;
            }
        }
        return repaired;
    }

    /** @return the repaired JSON, or null if the text needs no change (or is not JSON) */
    static String repairJson(String json) {
        if (json == null || !json.contains("\"color\"")) return null;
        JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (RuntimeException e) {
            return null;
        }
        return repairElement(root) ? GSON.toJson(root) : null;
    }

    private static boolean repairElement(JsonElement element) {
        boolean changed = false;
        if (element instanceof JsonArray array) {
            for (JsonElement child : array) changed |= repairElement(child);
        } else if (element instanceof JsonObject object) {
            if (object.get("color") instanceof JsonPrimitive primitive && primitive.isString()) {
                String color = primitive.getAsString();
                if (!isValidColor(color)) {
                    String cleaned = color.toLowerCase(Locale.ROOT).replaceAll("[^a-z_#0-9]", "");
                    if (isValidColor(cleaned)) object.addProperty("color", cleaned);
                    else object.remove("color"); // e.g. "reset": inherit the parent's color
                    changed = true;
                }
            }
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) changed |= repairElement(entry.getValue());
        }
        return changed;
    }

    static boolean isValidColor(String color) {
        if (NAMED_COLORS.contains(color)) return true;
        return color.length() == 7 && color.charAt(0) == '#' && color.substring(1).matches("[0-9a-fA-F]{6}");
    }
}
