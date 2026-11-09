package jp.azisaba.lgw.ecplus;

import jp.azisaba.lgw.ecplus.utils.Chat;
import jp.azisaba.lgw.ecplus.utils.nbt.Nbt;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Container;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class InventoryData {
    private static final int SERIALIZATION_MAGIC = 0x45435032; // ECP2
    /** Optional trailer after the pages (older versions stop reading before it, so it stays compatible). */
    private static final int TRAILER_MAGIC = 0x45435046; // ECPF
    /** The PDC of items imported from legacy YAML has been repaired (see {@link LegacyPdcRepair}). */
    private static final int FLAG_LEGACY_PDC_REPAIRED = 1;
    private final Map<Integer, Inventory> inventories = new HashMap<>();
    private int flags = 0;
    private final UUID uuid;
    private final DatabaseManager database;

    public InventoryData(UUID uuid, DatabaseManager database) {
        this(uuid, database, true);
    }

    InventoryData(UUID uuid, DatabaseManager database, boolean load) {
        this.uuid = uuid;
        this.database = database;
        if (load) load();
    }

    public int addItemInEmptySlot(ItemStack item) {
        for (int i = 0; i < EnderChestPlus.MAX_MAIN_INVENTORY_PAGES * 54; i++) {
            Inventory inventory = inventories.get(i);
            if (inventory == null) continue;
            int slot = inventory.firstEmpty();
            if (slot >= 0) {
                inventory.setItem(slot, item);
                return i;
            }
        }
        return -1;
    }

    /**
     * Adds the item to the first pages that can hold it (merging with existing stacks).
     *
     * @return the amount that could not be stored (0 if everything was stored)
     */
    public int addItem(ItemStack item) {
        ItemStack remaining = item.clone();
        for (int i = 0; i < EnderChestPlus.MAX_MAIN_INVENTORY_PAGES * 54 && remaining.getAmount() > 0; i++) {
            Inventory inventory = inventories.get(i);
            if (inventory == null) continue;
            Map<Integer, ItemStack> left = inventory.addItem(remaining);
            if (left.isEmpty()) {
                return 0;
            }
            remaining = left.values().iterator().next();
        }
        return remaining.getAmount();
    }

    private void load() {
        try {
            byte[] bytes = database.load(uuid);
            if (bytes == null) flags |= FLAG_LEGACY_PDC_REPAIRED; // nothing imported before the repair existed
            if (bytes != null) {
                if (isCurrentFormat(bytes)) {
                    deserialize(bytes);
                    int repaired = repairLegacyPdcOnce();
                    repaired += repairLegacyStorageBoxes();
                    if (repaired > 0) save(false);
                } else if (loadLegacyYaml()) {
                    save(false);
                } else {
                    throw new IOException("Unsupported legacy inventory data format");
                }
            } else if (loadLegacyYaml()) {
                save(false);
            }
        } catch (SQLException | IOException | InvalidConfigurationException e) {
            throw new IllegalStateException("Could not load inventory data for " + uuid, e);
        }
        for (int i = 0; i < 18; i++) inventories.computeIfAbsent(i, this::createInventory);
    }

    public boolean reloadFromLegacyYaml() throws IOException, InvalidConfigurationException {
        return reloadFromLegacyYaml(uuid);
    }

    /**
     * Reloads this inventory from the legacy YAML file of the given source UUID
     * (useful when the player's UUID changed, e.g. offline-mode UUID -> Mojang UUID).
     */
    public boolean reloadFromLegacyYaml(UUID sourceUuid) throws IOException, InvalidConfigurationException {
        File file = new File(EnderChestPlus.getInventoryDataFile(), sourceUuid + ".yml");
        if (!file.isFile()) return false;
        inventories.clear();
        if (loadLegacyYaml(file)) {
            for (int i = 0; i < 18; i++) inventories.computeIfAbsent(i, this::createInventory);
            return save(false);
        }
        return false;
    }

    public boolean loadLegacyYaml() throws IOException, InvalidConfigurationException {
        return loadLegacyYaml(new File(EnderChestPlus.getInventoryDataFile(), uuid + ".yml"));
    }

    /**
     * Data imported from legacy YAML before {@link LegacyPdcRepair} existed has quote-wrapped PDC values.
     * They are repaired once per player; afterwards the flag keeps quotes that plugins write on purpose.
     *
     * @return the number of repaired items (the flag change alone also counts, so it gets saved)
     */
    private int repairLegacyPdcOnce() {
        if ((flags & FLAG_LEGACY_PDC_REPAIRED) != 0) return 0;
        flags |= FLAG_LEGACY_PDC_REPAIRED;
        int repaired = 0;
        for (Inventory inventory : inventories.values()) repaired += LegacyPdcRepair.repairInventory(inventory);
        if (repaired > 0) {
            MigrationLog.info("Repaired the legacy PDC of " + repaired + " item(s) for " + uuid + ".");
        }
        return repaired + 1;
    }

    public boolean loadLegacyYaml(File file) throws IOException, InvalidConfigurationException {
        if (!file.isFile()) return false;
        flags |= FLAG_LEGACY_PDC_REPAIRED; // repaired item by item below
        String yaml = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        yaml = sanitizeLegacyYaml(yaml);
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString(yaml);
        } catch (Throwable t) {
            MigrationLog.warn("Failed to parse legacy YAML for " + uuid + ": " + t.getMessage());
            return false;
        }
        Map<?, ?> raw = parseRawYaml(yaml);
        for (String pageKey : config.getKeys(false)) {
            int page = positiveInt(pageKey);
            if (page < 0 || config.getConfigurationSection(pageKey) == null) continue;
            Inventory inventory = createInventory(page);
            for (String slotKey : config.getConfigurationSection(pageKey).getKeys(false)) {
                int slot = positiveInt(slotKey);
                ItemStack item = null;
                try {
                    item = readLegacyItem(config, raw, pageKey + "." + slotKey);
                } catch (Throwable t) {
                    MigrationLog.warn("Could not deserialize item at " + pageKey + "." + slotKey + " for " + uuid + ": " + t.getMessage());
                }
                if (item == null || item.getType() == Material.AIR) {
                    // Bukkit could not read it (e.g. a shulker box with one bad item): rebuild it from the raw
                    // NBT through the DataFixer instead of dropping it together with its contents
                    item = readLegacyItemViaDataFixer(raw, pageKey + "." + slotKey);
                    if (item != null) {
                        MigrationLog.info("Recovered " + item.getType() + " at " + pageKey + "." + slotKey + " for " + uuid + " from raw NBT.");
                    }
                }
                if (slot >= 0 && slot < inventory.getSize() && item != null && item.getType() != Material.AIR) {
                    fixLegacyContainerItem(item, raw, pageKey + "." + slotKey);
                    ItemStack pdcFixed = LegacyPdcRepair.repair(item);
                    if (pdcFixed != null) item = pdcFixed;
                    inventory.setItem(slot, item);
                }
            }
            inventories.put(page, inventory);
        }
        return true;
    }

    public static String sanitizeLegacyYaml(String yaml) {
        if (yaml == null || yaml.isBlank()) return yaml;
        String[] lines = yaml.split("\r?\n", -1);
        StringBuilder sb = new StringBuilder(yaml.length() + 256);
        boolean inPdc = false;
        int pdcIndent = -1;
        int blockScalarIndent = -1; // indent of the key whose block scalar content is being passed through

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();

            if (inPdc) {
                int currentIndent = countLeadingSpaces(line);
                if (!trimmed.isEmpty() && currentIndent <= pdcIndent) {
                    inPdc = false;
                    pdcIndent = -1;
                    blockScalarIndent = -1;
                } else if (blockScalarIndent >= 0 && (trimmed.isEmpty() || currentIndent > blockScalarIndent)) {
                    // content of a block scalar: keep as is
                } else if (!trimmed.isEmpty()) {
                    blockScalarIndent = -1;
                    if (isBlockScalarLine(line)) {
                        blockScalarIndent = currentIndent;
                    } else if (isParentOfNested(lines, i, currentIndent)) {
                        // key with nested mapping/list: keep as is
                    } else {
                        line = sanitizePdcLine(line);
                    }
                }
            }

            if (!inPdc && trimmed.startsWith("PublicBukkitValues:")) {
                inPdc = true;
                pdcIndent = countLeadingSpaces(line);
            }

            sb.append(line);
            if (i < lines.length - 1) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    /** True if the line has an empty value and the next non-blank line is indented deeper (nested structure). */
    private static boolean isParentOfNested(String[] lines, int index, int indent) {
        int sepIdx = findSeparator(lines[index]);
        if (sepIdx < 0 || !lines[index].substring(sepIdx + 1).trim().isEmpty()) return false;
        for (int j = index + 1; j < lines.length; j++) {
            if (lines[j].trim().isEmpty()) continue;
            return countLeadingSpaces(lines[j]) > indent;
        }
        return false;
    }

    private static int countLeadingSpaces(String s) {
        int count = 0;
        while (count < s.length() && s.charAt(count) == ' ') count++;
        return count;
    }

    private static int findSeparator(String line) {
        boolean inQuotes = false;
        char quoteChar = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (!inQuotes && (c == '"' || c == '\'')) {
                inQuotes = true;
                quoteChar = c;
            } else if (inQuotes && c == quoteChar) {
                inQuotes = false;
            } else if (!inQuotes && c == ':' && (i + 1 == line.length() || Character.isWhitespace(line.charAt(i + 1)))) {
                return i;
            }
        }
        return -1;
    }

    /** True if the line's value is a YAML block scalar indicator such as "|", "|-", ">" or "|2+". */
    private static boolean isBlockScalarLine(String line) {
        int sepIdx = findSeparator(line);
        if (sepIdx < 0) return false;
        return line.substring(sepIdx + 1).trim().matches("[|>][+-]?\\d?[+-]?");
    }

    private static String sanitizePdcLine(String line) {
        int sepIdx = findSeparator(line);
        if (sepIdx < 0) return line;

        String prefix = line.substring(0, sepIdx + 1);
        String value = line.substring(sepIdx + 1).trim();

        if (value.isEmpty() || value.equals("\"\"") || value.equals("''")) {
            return prefix + " '\"\"'";
        }

        // Check if array or compound
        if (value.startsWith("[") || value.startsWith("{")) {
            return line;
        }

        // Extract inner content if quoted
        String content = value;
        if (content.startsWith("\"") && content.endsWith("\"") && content.length() >= 2) {
            content = content.substring(1, content.length() - 1);
        } else if (content.startsWith("'") && content.endsWith("'") && content.length() >= 2) {
            content = content.substring(1, content.length() - 1);
        }

        // Every value is wrapped the same way (Paper keeps the quotes, so a value that already had them
        // must be wrapped again); LegacyPdcRepair removes exactly one pair and restores the 1.15.2 type.
        // Escape single quotes for YAML single-quoted string
        content = content.replace("'", "''");
        return prefix + " '\"" + content + "\"'";
    }

    /** Root NBT keys of 1.15.2 items that vanilla/Bukkit handles itself (everything else is plugin data). */
    private static final java.util.Set<String> VANILLA_LEGACY_TAGS = java.util.Set.of(
            "display", "Enchantments", "StoredEnchantments", "Damage", "RepairCost", "Unbreakable", "HideFlags",
            "AttributeModifiers", "CustomModelData", "PublicBukkitValues", "BlockEntityTag", "BlockStateTag",
            "SkullOwner", "Potion", "CustomPotionEffects", "CustomPotionColor", "Fireworks", "Explosion",
            "Trim", "map", "map_scale_direction", "Decorations", "pages", "author", "title", "generation",
            "resolved", "EntityTag", "BucketVariantTag", "Items", "ChargedProjectiles", "Charged",
            "LodestonePos", "LodestoneDimension", "LodestoneTracked", "CanDestroy", "CanPlaceOn",
            "Effects", "Recipes", "Tags");

    /**
     * Reads an item from legacy YAML and restores plugin-specific root NBT tags
     * (e.g. StorageBox's storageBoxType) that Bukkit drops when loading a 1.15.2 item,
     * by putting them into the item's custom_data component.
     */
    private static ItemStack readLegacyItem(YamlConfiguration config, Map<?, ?> raw, String path) {
        ItemStack item = config.getItemStack(path);
        if (item == null || item.getType() == Material.AIR) return item;

        String internalBase64 = rawString(raw, path + ".meta.internal");
        if (internalBase64 == null || internalBase64.isBlank()) return item;

        try {
            Nbt.CompoundTag legacyRoot = Nbt.readCompressed(Base64.getDecoder().decode(internalBase64));
            Map<String, Nbt.Tag> extra = new HashMap<>();
            for (Map.Entry<String, Nbt.Tag> entry : legacyRoot.getTags().entrySet()) {
                if (!VANILLA_LEGACY_TAGS.contains(entry.getKey())) {
                    extra.put(entry.getKey(), entry.getValue());
                }
            }
            if (extra.isEmpty()) return item;
            MigrationLog.info("Restoring legacy custom tags " + extra.keySet() + " at " + path);

            Nbt.CompoundTag itemRoot = Nbt.readCompressed(item.serializeAsBytes());
            Nbt.CompoundTag components = itemRoot.getCompound("components");
            if (components == null) {
                components = new Nbt.CompoundTag();
                itemRoot.put("components", components);
            }
            Nbt.CompoundTag customData = components.getCompound("minecraft:custom_data");
            if (customData == null) {
                customData = new Nbt.CompoundTag();
                components.put("minecraft:custom_data", customData);
            }
            boolean changed = false;
            for (Map.Entry<String, Nbt.Tag> entry : extra.entrySet()) {
                if (customData.get(entry.getKey()) == null) {
                    Nbt.Tag value = entry.getValue().copy();
                    if (entry.getKey().equals("storageBoxType") && value instanceof Nbt.StringTag type) {
                        value = new Nbt.StringTag(LegacyStorageBoxRepair.modernMaterialName(type.getValue()));
                    }
                    customData.put(entry.getKey(), value);
                    changed = true;
                }
            }
            if (!changed) return item;

            ItemStack restored = ItemStack.deserializeBytes(Nbt.writeCompressed(itemRoot));
            MigrationLog.info("Restored item components after custom_data merge: " + itemRoot.getCompound("components").getTags().keySet());
            return restored != null && restored.getType() != Material.AIR ? restored : item;
        } catch (Exception e) {
            MigrationLog.warn("Could not restore legacy custom tags at " + path + ": " + e.getMessage());
            return item;
        }
    }

    /**
     * Rebuilds a legacy item from its raw YAML (type, amount, display name, lore and the "internal" NBT,
     * which holds BlockEntityTag and plugin tags) and lets Minecraft's DataFixer upgrade it.
     * Used when Bukkit's own deserialization fails, so a shulker box keeps its contents.
     */
    static ItemStack readLegacyItemViaDataFixer(Map<?, ?> raw, String path) {
        String type = rawString(raw, path + ".type");
        if (type == null || type.isBlank()) return null;
        try {
            Nbt.CompoundTag tag = new Nbt.CompoundTag();
            String internal = rawString(raw, path + ".meta.internal");
            if (internal != null && !internal.isBlank()) {
                tag = Nbt.readCompressed(Base64.getDecoder().decode(internal));
            }
            if (LegacyTextRepair.repairTag(tag) > 0) {
                MigrationLog.info("Repaired invalid text colors at " + path + " before the DataFixer.");
            }
            Nbt.CompoundTag display = tag.getCompound("display");
            if (display == null) display = new Nbt.CompoundTag();
            String name = rawString(raw, path + ".meta.display-name");
            if (name != null && display.get("Name") == null) display.putString("Name", jsonText(name));
            if (rawValue(raw, path + ".meta.lore") instanceof java.util.List<?> lore && display.get("Lore") == null) {
                Nbt.ListTag loreTag = new Nbt.ListTag();
                for (Object line : lore) loreTag.add(new Nbt.StringTag(jsonText(String.valueOf(line))));
                display.put("Lore", loreTag);
            }
            if (!display.getTags().isEmpty()) tag.put("display", display);
            // PDC is not part of "internal": take it from the YAML so the rebuilt item keeps it
            Object pdc = rawValue(raw, path + ".meta.PublicBukkitValues");
            if (pdc instanceof Map<?, ?> && tag.get("PublicBukkitValues") == null) {
                tag.put("PublicBukkitValues", LegacyPdcRepair.fromRawYaml(pdc));
            }

            Object amount = rawValue(raw, path + ".amount");
            Object version = rawValue(raw, path + ".v");
            Nbt.CompoundTag item = new Nbt.CompoundTag();
            item.putString("id", "minecraft:" + type.toLowerCase(java.util.Locale.ROOT));
            item.putByte("Count", (byte) (amount instanceof Number n ? n.intValue() : 1));
            if (!tag.getTags().isEmpty()) item.put("tag", tag);
            item.putInt("DataVersion", version instanceof Number n && n.intValue() > 0 ? n.intValue() : 2230); // 1.15.2
            ItemStack restored = ItemStack.deserializeBytes(Nbt.writeCompressed(item));
            return restored == null || restored.getType() == Material.AIR ? null : restored;
        } catch (Throwable t) {
            MigrationLog.warn("Could not rebuild legacy item at " + path + " from raw NBT: " + t.getMessage());
            return null;
        }
    }

    /** 1.15.2 stores display names as JSON text; older YAML may hold legacy "§" text instead. */
    private static String jsonText(String value) {
        String trimmed = value.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[") || trimmed.startsWith("\"")) return value;
        return "{\"text\":\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
    }

    /**
     * YamlConfiguration deserializes "==: ItemStack" maps into objects while parsing, so the raw
     * "meta.internal" string is no longer reachable through it. Parse the YAML again as plain maps.
     */
    static Map<?, ?> parseRawYaml(String yaml) {
        try {
            Object loaded = new org.yaml.snakeyaml.Yaml().load(yaml);
            return loaded instanceof Map<?, ?> m ? m : Map.of();
        } catch (Throwable t) {
            MigrationLog.warn("Could not parse raw legacy YAML: " + t.getMessage());
            return Map.of();
        }
    }

    private static Object rawValue(Map<?, ?> raw, String path) {
        Object current = raw;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) return null;
            Object next = null;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (String.valueOf(e.getKey()).equals(part)) {
                    next = e.getValue();
                    break;
                }
            }
            current = next;
        }
        return current;
    }

    static String rawString(Map<?, ?> raw, String path) {
        Object value = rawValue(raw, path);
        return value == null ? null : value.toString();
    }

    private static void fixLegacyContainerItem(ItemStack item, Map<?, ?> raw, String path) {
        if (item == null) return;
        if (!(item.getItemMeta() instanceof BlockStateMeta bsm)) return;
        if (!(bsm.getBlockState() instanceof Container container)) return;

        String internalBase64 = rawString(raw, path + ".meta.internal");
        if (internalBase64 == null || internalBase64.isBlank()) return;

        try {
            Object versionValue = rawValue(raw, path + ".v");
            int dataVersion = versionValue instanceof Number n ? n.intValue() : 3465; // default to 1.20.2 if absent
            if (dataVersion <= 0) dataVersion = 3465;

            byte[] nbtBytes = Base64.getDecoder().decode(internalBase64);
            Nbt.CompoundTag root = Nbt.readCompressed(nbtBytes);
            Nbt.CompoundTag blockEntityTag = root.getCompound("BlockEntityTag");
            if (blockEntityTag == null) return;

            Nbt.ListTag itemsList = blockEntityTag.getList("Items");
            if (itemsList == null || itemsList.isEmpty()) return;

            Inventory containerInv = container.getInventory();
            boolean modified = false;

            for (Nbt.Tag tag : itemsList.getElements()) {
                if (!(tag instanceof Nbt.CompoundTag itemCompound)) continue;
                Number slotNumber = itemCompound.getNumber("Slot");
                if (slotNumber == null) continue;
                int itemSlot = slotNumber.intValue();
                if (itemSlot < 0 || itemSlot >= containerInv.getSize()) continue;

                // Build standalone ItemStack NBT compound to feed into DataFixerUpper via deserializeBytes
                Nbt.CompoundTag standalone = new Nbt.CompoundTag();
                standalone.putInt("DataVersion", dataVersion);
                for (Map.Entry<String, Nbt.Tag> entry : itemCompound.getTags().entrySet()) {
                    if (!entry.getKey().equals("Slot")) {
                        standalone.put(entry.getKey(), entry.getValue().copy());
                    }
                }
                if (LegacyTextRepair.repairTag(standalone.getCompound("tag")) > 0) {
                    MigrationLog.info("Repaired invalid text colors in container slot " + itemSlot + " at " + path + ".");
                }

                try {
                    byte[] standaloneBytes = Nbt.writeCompressed(standalone);
                    ItemStack deserialized = ItemStack.deserializeBytes(standaloneBytes);
                    if (deserialized != null && deserialized.getType() != Material.AIR) {
                        containerInv.setItem(itemSlot, deserialized);
                        modified = true;
                    }
                } catch (Exception e) {
                    MigrationLog.warn("Failed to deserialize legacy container item slot " + itemSlot + " at " + path + ": " + e.getMessage());
                }
            }

            if (modified) {
                bsm.setBlockState(container);
                item.setItemMeta(bsm);
            }
        } catch (Exception e) {
            MigrationLog.warn("Failed to fix legacy container item at " + path + ": " + e.getMessage());
        }
    }

    /**
     * Gives StorageBoxes that lost their NBT in an earlier migration their legacy tags back
     * (see {@link LegacyStorageBoxRepair}). The legacy YAML is read only when a suspicious box exists.
     *
     * @return the number of repaired boxes
     */
    int repairLegacyStorageBoxes() {
        boolean suspicious = false;
        for (Inventory inventory : inventories.values()) {
            for (ItemStack item : inventory.getContents()) {
                if (LegacyStorageBoxRepair.mayNeedRepair(item)) {
                    suspicious = true;
                    break;
                }
            }
            if (suspicious) break;
        }
        if (!suspicious) return 0;

        Map<String, Nbt.CompoundTag> legacy = new HashMap<>();
        for (File file : legacyYamlCandidates()) {
            try {
                legacy.putAll(LegacyStorageBoxRepair.indexLegacyBoxes(
                        parseRawYaml(sanitizeLegacyYaml(Files.readString(file.toPath(), StandardCharsets.UTF_8)))));
            } catch (IOException e) {
                MigrationLog.warn("Could not read " + file.getName() + ": " + e.getMessage());
            }
        }
        if (legacy.isEmpty()) return 0;

        int repaired = 0;
        for (Map.Entry<Integer, Inventory> entry : inventories.entrySet()) {
            int page = entry.getKey() + 1;
            repaired += LegacyStorageBoxRepair.repairInventory(entry.getValue(), legacy, message ->
                    MigrationLog.info(uuid + " page " + page + ": " + message));
        }
        return repaired;
    }

    /** The legacy YAML of this UUID, and of the offline-mode UUID of the same name (UUID changed since 1.15.2). */
    private java.util.List<File> legacyYamlCandidates() {
        java.util.List<File> files = new java.util.ArrayList<>();
        File own = new File(EnderChestPlus.getInventoryDataFile(), uuid + ".yml");
        if (own.isFile()) files.add(own);
        try {
            String name = Bukkit.getOfflinePlayer(uuid).getName();
            if (name != null) {
                UUID offline = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
                File old = new File(EnderChestPlus.getInventoryDataFile(), offline + ".yml");
                if (!offline.equals(uuid) && old.isFile()) files.add(old);
            }
        } catch (RuntimeException ignored) {
            // no server (tests) or unknown player
        }
        return files;
    }

    /**
     * Finds shulker boxes (and other containers) whose contents were lost or damaged by an earlier migration,
     * by comparing them with the same page/slot of the legacy YAML, and restores them when {@code apply} is true.
     * <p>
     * This is not run automatically: a container a player emptied on purpose looks the same as a broken one,
     * so an automatic restore would duplicate items. An admin runs it for a reported player and checks the
     * preview first.
     *
     * @return one line per suspicious container (restored ones when applied)
     */
    public java.util.List<String> repairContainersFromLegacyYaml(boolean apply) throws IOException {
        java.util.List<String> report = new java.util.ArrayList<>();
        java.util.List<File> files = legacyYamlCandidates();
        if (files.isEmpty()) {
            report.add("レガシーYAMLがありません");
            return report;
        }
        File file = files.get(0);
        String yaml = sanitizeLegacyYaml(Files.readString(file.toPath(), StandardCharsets.UTF_8));
        Map<?, ?> raw = parseRawYaml(yaml);

        for (Map.Entry<Integer, Inventory> entry : new java.util.TreeMap<>(inventories).entrySet()) {
            int page = entry.getKey();
            Inventory inventory = entry.getValue();
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                ItemStack currentItem = inventory.getItem(slot);
                if (currentItem == null || currentItem.getType() == Material.AIR) continue;
                if (!(currentItem.getItemMeta() instanceof BlockStateMeta bsm)
                        || !(bsm.getBlockState() instanceof Container currentContainer)) continue;
                String path = page + "." + slot;
                String internalBase64 = rawString(raw, path + ".meta.internal");
                if (internalBase64 == null || internalBase64.isBlank()) continue;
                String legacyType = rawString(raw, path + ".type");
                if (legacyType == null || !legacyType.equalsIgnoreCase(currentItem.getType().name())) continue;
                if (!isContainerSuspectedCorrupted(currentContainer, internalBase64)) continue;

                int before = countItems(currentContainer.getInventory());
                String where = "ページ" + (page + 1) + " スロット" + slot + " " + currentItem.getType();
                if (!apply) {
                    report.add(where + "（現在 " + before + "スタック → 旧データ " + legacyStackCount(internalBase64) + "スタック）");
                    continue;
                }
                ItemStack repaired = currentItem.clone();
                fixLegacyContainerItem(repaired, raw, path);
                if (!(repaired.getItemMeta() instanceof BlockStateMeta repairedMeta)
                        || !(repairedMeta.getBlockState() instanceof Container repairedContainer)) continue;
                inventory.setItem(slot, repaired);
                report.add(where + "（" + before + " → " + countItems(repairedContainer.getInventory()) + "スタック）");
                Bukkit.getLogger().info("[EnderChestPlus] Restored container contents at page " + (page + 1) + " slot " + slot + " for " + uuid + " from " + file.getName() + ".");
            }
        }
        return report;
    }

    private static int countItems(Inventory inventory) {
        int count = 0;
        for (ItemStack item : inventory.getContents()) if (item != null && item.getType() != Material.AIR) count++;
        return count;
    }

    private static int legacyStackCount(String internalBase64) {
        try {
            Nbt.CompoundTag blockEntity = Nbt.readCompressed(Base64.getDecoder().decode(internalBase64)).getCompound("BlockEntityTag");
            Nbt.ListTag items = blockEntity == null ? null : blockEntity.getList("Items");
            return items == null ? 0 : items.size();
        } catch (Exception e) {
            return -1;
        }
    }

    private static boolean isContainerSuspectedCorrupted(Container currentContainer, String internalBase64) {
        try {
            byte[] nbtBytes = Base64.getDecoder().decode(internalBase64);
            Nbt.CompoundTag root = Nbt.readCompressed(nbtBytes);
            Nbt.CompoundTag blockEntityTag = root.getCompound("BlockEntityTag");
            if (blockEntityTag == null) return false;

            Nbt.ListTag itemsList = blockEntityTag.getList("Items");
            if (itemsList == null || itemsList.isEmpty()) return false;

            int yamlItemCount = 0;
            Map<Integer, Nbt.CompoundTag> yamlItemsBySlot = new HashMap<>();

            for (Nbt.Tag tag : itemsList.getElements()) {
                if (tag instanceof Nbt.CompoundTag itemCompound) {
                    yamlItemCount++;
                    Number slot = itemCompound.getNumber("Slot");
                    if (slot != null) {
                        yamlItemsBySlot.put(slot.intValue(), itemCompound);
                    }
                }
            }

            if (yamlItemCount == 0) return false;

            int currentItemCount = 0;
            boolean allCurrentCountOne = true;
            int matchedCorruptedItems = 0;

            for (int slot = 0; slot < currentContainer.getInventory().getSize(); slot++) {
                ItemStack item = currentContainer.getInventory().getItem(slot);
                if (item != null && item.getType() != Material.AIR) {
                    currentItemCount++;
                    if (item.getAmount() > 1) {
                        allCurrentCountOne = false;
                    }

                    Nbt.CompoundTag yamlItem = yamlItemsBySlot.get(slot);
                    if (yamlItem != null) {
                        String yamlId = yamlItem.getString("id");
                        Number yamlCount = yamlItem.getNumber("Count");
                        if (yamlId != null) {
                            String normalizedYamlId = yamlId.startsWith("minecraft:") ? yamlId.substring(10) : yamlId;
                            String currentId = item.getType().getKey().getKey();
                            if (normalizedYamlId.equalsIgnoreCase(currentId)) {
                                if (yamlCount != null && yamlCount.intValue() > 1 && item.getAmount() == 1) {
                                    matchedCorruptedItems++;
                                }
                                Nbt.CompoundTag yamlTag = yamlItem.getCompound("tag");
                                if (yamlTag != null && yamlTag.getCompound("PublicBukkitValues") != null) {
                                    if (!item.hasItemMeta() || item.getItemMeta().getPersistentDataContainer().isEmpty()) {
                                        matchedCorruptedItems++;
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Case 1: Items exist, all counts are 1, and matches corrupted stacked items or missing PDC from legacy YAML
            if (matchedCorruptedItems > 0 && allCurrentCountOne) {
                return true;
            }

            // Case 2: Container is completely empty but legacy YAML had items
            if (currentItemCount == 0 && yamlItemCount > 0) {
                return true;
            }

            return false;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void deserialize(byte[] bytes) throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (input.readInt() != SERIALIZATION_MAGIC) throw new IOException("Unsupported inventory data format");
            int pages = input.readInt();
            for (int p = 0; p < pages; p++) {
                int page = input.readInt();
                int size = input.readInt();
                Inventory inventory = createInventory(page);
                for (int slot = 0; slot < size; slot++) {
                    int itemLength = input.readInt();
                    ItemStack item = itemLength == 0 ? null : ItemStack.deserializeBytes(input.readNBytes(itemLength));
                    if (slot < inventory.getSize()) inventory.setItem(slot, item);
                }
                inventories.put(page, inventory);
            }
            if (input.available() >= Integer.BYTES * 2 && input.readInt() == TRAILER_MAGIC) {
                flags = input.readInt();
            }
        }
    }

    public synchronized boolean save(boolean ignoredAsyncSave) {
        try {
            database.save(uuid, serialize());
            return true;
        } catch (SQLException | IOException e) {
            Bukkit.getLogger().severe("Could not save inventory data for " + uuid + ": " + e.getMessage());
            return false;
        }
    }

    private byte[] serialize() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(SERIALIZATION_MAGIC);
            output.writeInt(inventories.size());
            for (Map.Entry<Integer, Inventory> entry : inventories.entrySet()) {
                output.writeInt(entry.getKey());
                output.writeInt(entry.getValue().getSize());
                for (ItemStack item : entry.getValue().getContents()) {
                    byte[] itemBytes = item == null ? new byte[0] : item.serializeAsBytes();
                    output.writeInt(itemBytes.length);
                    output.write(itemBytes);
                }
            }
            output.writeInt(TRAILER_MAGIC);
            output.writeInt(flags);
        }
        return bytes.toByteArray();
    }

    public Inventory getInventory(int num) { return inventories.get(num); }

    public java.util.Collection<Inventory> getInventories() { return java.util.List.copyOf(inventories.values()); }

    public void initializeInventory(int page) { inventories.put(page, createInventory(page)); }

    public InventoryData migrateAs(UUID targetUuid) {
        InventoryData data = new InventoryData(targetUuid, database, false);
        data.flags = flags;
        for (Map.Entry<Integer, Inventory> entry : inventories.entrySet()) {
            Inventory copy = data.createInventory(entry.getKey());
            copy.setContents(entry.getValue().getContents());
            data.inventories.put(entry.getKey(), copy);
        }
        return data;
    }

    private Inventory createInventory(int page) {
        return Bukkit.createInventory(null, 54,
                Chat.component(Chat.f("{0} &e- &cPage {1}", EnderChestPlus.enderChestTitlePrefix, page + 1)));
    }

    private static int positiveInt(String value) {
        try { int i = Integer.parseInt(value); return i < 0 ? -1 : i; }
        catch (NumberFormatException ignored) { return -1; }
    }

    private static boolean isCurrentFormat(byte[] bytes) {
        if (bytes.length < Integer.BYTES) return false;
        return ((bytes[0] & 0xFF) << 24 | (bytes[1] & 0xFF) << 16 | (bytes[2] & 0xFF) << 8 | bytes[3] & 0xFF)
                == SERIALIZATION_MAGIC;
    }
}
