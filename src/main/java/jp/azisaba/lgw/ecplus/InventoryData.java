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
    private final Map<Integer, Inventory> inventories = new HashMap<>();
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

    private void load() {
        try {
            byte[] bytes = database.load(uuid);
            if (bytes != null) {
                if (isCurrentFormat(bytes)) {
                    deserialize(bytes);
                    try {
                        if (repairCorruptedContainersFromYaml()) {
                            save(false);
                        }
                    } catch (Throwable t) {
                        Bukkit.getLogger().warning("[EnderChestPlus] Failed to check/repair legacy data for " + uuid + ": " + t.getMessage());
                    }
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
        File file = new File(EnderChestPlus.getInventoryDataFile(), uuid + ".yml");
        if (!file.isFile()) return false;
        inventories.clear();
        if (loadLegacyYaml()) {
            for (int i = 0; i < 18; i++) inventories.computeIfAbsent(i, this::createInventory);
            return save(false);
        }
        return false;
    }

    public boolean loadLegacyYaml() throws IOException, InvalidConfigurationException {
        File file = new File(EnderChestPlus.getInventoryDataFile(), uuid + ".yml");
        if (!file.isFile()) return false;
        String yaml = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        yaml = sanitizeLegacyYaml(yaml);
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString(yaml);
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[EnderChestPlus] Failed to parse legacy YAML for " + uuid + ": " + t.getMessage());
            return false;
        }
        for (String pageKey : config.getKeys(false)) {
            int page = positiveInt(pageKey);
            if (page < 0 || config.getConfigurationSection(pageKey) == null) continue;
            Inventory inventory = createInventory(page);
            for (String slotKey : config.getConfigurationSection(pageKey).getKeys(false)) {
                int slot = positiveInt(slotKey);
                ItemStack item = null;
                try {
                    item = config.getItemStack(pageKey + "." + slotKey);
                } catch (Throwable t) {
                    Bukkit.getLogger().warning("[EnderChestPlus] Could not deserialize item at " + pageKey + "." + slotKey + " for " + uuid + ": " + t.getMessage());
                }
                if (slot >= 0 && slot < inventory.getSize() && item != null && item.getType() != Material.AIR) {
                    fixLegacyContainerItem(item, config, pageKey + "." + slotKey);
                    inventory.setItem(slot, item);
                }
            }
            inventories.put(page, inventory);
        }
        return true;
    }

    public static String sanitizeLegacyYaml(String yaml) {
        if (yaml == null || yaml.isBlank()) return yaml;
        // Fix empty strings in PublicBukkitValues (and other namespaced YAML mappings) that cause Paper 1.21's
        // CraftNBTTagConfigSerializer to crash:
        // Brigadier's TagParser throws CommandSyntaxException on 0-length strings.
        // Replacing empty values with '""' (quoted empty string in SNBT) allows TagParser to parse them cleanly as StringTag("").
        return yaml.replaceAll("(?m)(^\\s*[\"']?[a-zA-Z0-9_.-]+:[a-zA-Z0-9_.-/]+[\"']?\\s*:\\s*)(?:\"\"|''|)\\s*$", "$1'\"\"'");
    }

    private static void fixLegacyContainerItem(ItemStack item, YamlConfiguration config, String path) {
        if (item == null) return;
        if (!(item.getItemMeta() instanceof BlockStateMeta bsm)) return;
        if (!(bsm.getBlockState() instanceof Container container)) return;

        String internalBase64 = config.getString(path + ".meta.internal");
        if (internalBase64 == null || internalBase64.isBlank()) return;

        try {
            int dataVersion = config.getInt(path + ".v", 3465); // default to 1.20.2 if absent
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

                try {
                    byte[] standaloneBytes = Nbt.writeCompressed(standalone);
                    ItemStack deserialized = ItemStack.deserializeBytes(standaloneBytes);
                    if (deserialized != null && deserialized.getType() != Material.AIR) {
                        containerInv.setItem(itemSlot, deserialized);
                        modified = true;
                    }
                } catch (Exception e) {
                    Bukkit.getLogger().warning("Failed to deserialize legacy container item slot " + itemSlot + " at " + path + ": " + e.getMessage());
                }
            }

            if (modified) {
                bsm.setBlockState(container);
                item.setItemMeta(bsm);
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("Failed to fix legacy container item at " + path + ": " + e.getMessage());
        }
    }

    private boolean repairCorruptedContainersFromYaml() {
        File file = new File(EnderChestPlus.getInventoryDataFile(), uuid + ".yml");
        if (!file.isFile()) return false;

        try {
            String yaml = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            yaml = sanitizeLegacyYaml(yaml);
            YamlConfiguration config = new YamlConfiguration();
            try {
                config.loadFromString(yaml);
            } catch (Throwable t) {
                Bukkit.getLogger().warning("[EnderChestPlus] Could not parse legacy YAML configuration for " + uuid + ": " + t.getMessage());
                return false;
            }

            boolean anyRepaired = false;

            for (Map.Entry<Integer, Inventory> entry : inventories.entrySet()) {
                int page = entry.getKey();
                Inventory inventory = entry.getValue();
                String pageKey = String.valueOf(page);
                if (config.getConfigurationSection(pageKey) == null) continue;

                for (int slot = 0; slot < inventory.getSize(); slot++) {
                    ItemStack currentItem = inventory.getItem(slot);
                    if (currentItem == null || currentItem.getType() == Material.AIR) continue;

                    String slotKey = String.valueOf(slot);
                    String path = pageKey + "." + slotKey;
                    String internalBase64 = config.getString(path + ".meta.internal");
                    if (internalBase64 == null || internalBase64.isBlank()) continue;

                    if (currentItem.getItemMeta() instanceof BlockStateMeta bsm && bsm.getBlockState() instanceof Container currentContainer) {
                        if (isContainerSuspectedCorrupted(currentContainer, internalBase64)) {
                            fixLegacyContainerItem(currentItem, config, path);
                            inventory.setItem(slot, currentItem);
                            anyRepaired = true;
                            Bukkit.getLogger().info("[EnderChestPlus] Auto-repaired corrupted container at page " + (page + 1) + " slot " + slot + " for " + uuid + " from legacy YAML.");
                        }
                    } else if (isTopLevelItemSuspectedCorrupted(currentItem, internalBase64)) {
                        ItemStack restored = config.getItemStack(path);
                        if (restored != null && restored.getType() != Material.AIR) {
                            inventory.setItem(slot, restored);
                            anyRepaired = true;
                            Bukkit.getLogger().info("[EnderChestPlus] Auto-repaired corrupted top-level item at page " + (page + 1) + " slot " + slot + " for " + uuid + " from legacy YAML.");
                        }
                    }
                }
            }

            return anyRepaired;
        } catch (Exception e) {
            Bukkit.getLogger().warning("[EnderChestPlus] Could not check/repair legacy data for " + uuid + ": " + e.getMessage());
            return false;
        }
    }

    private static boolean isTopLevelItemSuspectedCorrupted(ItemStack currentItem, String internalBase64) {
        try {
            byte[] nbtBytes = Base64.getDecoder().decode(internalBase64);
            Nbt.CompoundTag root = Nbt.readCompressed(nbtBytes);
            // If legacy YAML had PublicBukkitValues (PDC: MythicMobs, Soulbound, etc.) but DB item has empty PDC
            if (root.getCompound("PublicBukkitValues") != null) {
                return !currentItem.hasItemMeta() || currentItem.getItemMeta().getPersistentDataContainer().isEmpty();
            }
            return false;
        } catch (Exception ignored) {
            return false;
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
        }
        return bytes.toByteArray();
    }

    public Inventory getInventory(int num) { return inventories.get(num); }

    public void initializeInventory(int page) { inventories.put(page, createInventory(page)); }

    public InventoryData migrateAs(UUID targetUuid) {
        InventoryData data = new InventoryData(targetUuid, database, false);
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
